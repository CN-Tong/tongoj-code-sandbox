package com.tong.tongojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.tong.tongojcodesandbox.model.ExecuteCodeRequest;
import com.tong.tongojcodesandbox.model.ExecuteCodeResponse;
import com.tong.tongojcodesandbox.model.ExecuteMessage;
import com.tong.tongojcodesandbox.model.JudgeInfo;
import com.tong.tongojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 代码沙箱Docker实现
 */
public class JavaDockerCodeSandboxOldVersion implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;
    private static final String SECURITY_MANAGER_PATH = "src/main/resources/security";
    private static final String SECURITY_MANAGER_CLASS_NAME = "DefaultSecurityManager";
    private static final boolean FIRST_INIT = false;

    // public static void main(String[] args) {
    //     JavaDockerCodeSandboxOldVersion javaNativeCodeSandbox = new JavaDockerCodeSandboxOldVersion();
    //     ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
    //     String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
    //     // String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
    //     executeCodeRequest.setCode(code);
    //     executeCodeRequest.setLanguage("java");
    //     executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
    //     ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
    //     System.out.println("executeCodeResponse：" + executeCodeResponse);
    // }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 1.保存代码文件
        String userDir = System.getProperty("user.dir");
        // 为了兼容不同的操作系统，使用File.separator
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 2.编译代码
        String compileCmd = "javac -encoding utf-8 " + userCodeFile.getAbsolutePath();
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage compileExecuteMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(compileExecuteMessage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }
        // 3.创建容器，把编译好的文件上传到容器环境内
        // 3.1 创建默认的DockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 3.2 首次执行拉取镜像
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("下载镜像完成");
        }
        // 3.3 指定文件路径（Volumn）映射，把本地的文件同步到容器中
        HostConfig hostConfig = new HostConfig();
        // 限制内存100MB
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 3.4 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                // 禁用网络
                .withNetworkDisabled(true)
                // 限制不能向根目录写文件
                .withReadonlyRootfs(true)
                // 开启控制台输出
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                // 创建可交互容器
                .withTty(true)
                .exec();
        System.out.println("创建容器成功：" + createContainerResponse);
        String containerId = createContainerResponse.getId();
        // 4.启动容器，运行代码
        // 4.1 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        // 4.2 遍历输入案例，运行代码
        ArrayList<ExecuteMessage> messageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            // 创建运行代码的命令
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            String execId = execCreateCmdResponse.getId();
            // 收集message
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            // 用于收集程序执行时间
            long time = 0L;
            // 用于判断程序执行是否超时
            final boolean[] timeout = {true};
            // 回调接口获取程序的执行结果，通过StreamType来区分标准输出和错误输出。
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        // 内部类使用外部类的变量，改为final修饰的引用类型
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + message[0]);
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }
            };

            // 获取程序执行占用内存的最大值
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
                    Long memory = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + memory);
                    maxMemory[0] = Math.max(memory, maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            statsCmd.exec(statisticsResultCallback);

            // 异步执行
            try {
                // 获取程序执行的开始时间
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                // 获取程序执行的结束时间
                stopWatch.stop();
                // 获取程序执行时间
                time = stopWatch.getLastTaskTimeMillis();
                // 关闭统计命令
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            // 收集message
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            // 收集程序执行时间
            executeMessage.setTime(time);
            // 收集程序占用内存最大值
            executeMessage.setMemory(maxMemory[0]);
            messageArrayList.add(executeMessage);
        }
        // 5.收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        // 取运行时间的最大值
        long maxRunTime = 0;
        long maxRunMemory = 0;
        for (ExecuteMessage executeMessage : messageArrayList) {
            String errorMessage = executeMessage.getErrorMessage();
            // 如果执行存在错误
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 设置状态码3，用户提交的代码执行错误
                executeCodeResponse.setStatus(3);
                break;
            }
            // 将结果信息保存
            outputList.add(executeMessage.getMessage());
            // 判断是否是最大运行时间
            Long runTime = executeMessage.getTime();
            if (runTime != null) {
                maxRunTime = Math.max(maxRunTime, runTime);
            }
            // 判断是否是最大运行内存
            Long runMemory = executeMessage.getMemory();
            if(runMemory != null){
                maxRunMemory = Math.max(maxRunMemory, runMemory);
            }
        }
        // 正常运行完成
        if (outputList.size() == messageArrayList.size()) {
            // 设置状态码1
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        // 获取程序的执行时间（最大值）
        judgeInfo.setTime(maxRunTime);
        // 获取程序的执行内存
        judgeInfo.setMemory(maxRunMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 设置状态码2，表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
