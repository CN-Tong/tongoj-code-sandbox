package com.tong.tongojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.tong.tongojcodesandbox.model.*;
import com.tong.tongojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 1.保存代码文件
        File userCodeFile = saveCodeToFile(code);

        // 2.编译代码
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println("compileFileExecuteMessage: " + compileFileExecuteMessage);
        // 如果编译错误，设置状态码2
        if(compileFileExecuteMessage.getExitValue() != 0){
            return new ExecuteCodeResponse(
                    null, null, ExecuteCodeRespStatusEnum.COMPILE_ERROR.getValue(), new JudgeInfo());
        }

        // 3.执行代码
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        // 4.收集整理输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        // 5.文件清理
        boolean del = deleteFile(userCodeFile);
        if (!del) {
            log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
        }

        // 返回结果
        return outputResponse;
    }

    /**
     * 1.保存代码文件
     *
     * @param code
     * @return
     */
    public File saveCodeToFile(String code) {
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
        return userCodeFile;
    }

    /**
     * 2.编译代码
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = "javac -encoding utf-8 " + userCodeFile.getAbsolutePath();
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage compileExecuteMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            return compileExecuteMessage;
        } catch (IOException e) {
            // return getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 3.执行文件，获得执行结果列表
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        ArrayList<ExecuteMessage> messageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 创建一个守护线程，达到超时时间后中断运行程序的线程runProcess
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runProcess.isAlive()) {
                            System.out.println("超时了，中断");
                            runProcess.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage runExecuteMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                messageArrayList.add(runExecuteMessage);
                System.out.println(runExecuteMessage);
            } catch (IOException e) {
                // return getErrorResponse(e);
                throw new RuntimeException("执行错误：", e);
            }
        }
        return messageArrayList;
    }

    /**
     * 4.获取输出响应结果
     *
     * @param messageArrayList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> messageArrayList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        // 取运行时间的最大值
        long maxRunTime = 0;
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
        // judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5.清理文件
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println(del ? "删除成功" : "删除失败");
            return del;
        }
        return true;
    }
}
