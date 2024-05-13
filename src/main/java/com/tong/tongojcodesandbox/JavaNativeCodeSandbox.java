package com.tong.tongojcodesandbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.tong.tongojcodesandbox.model.ExecuteCodeRequest;
import com.tong.tongojcodesandbox.model.ExecuteCodeResponse;
import com.tong.tongojcodesandbox.model.ExecuteMessage;
import com.tong.tongojcodesandbox.model.JudgeInfo;
import com.tong.tongojcodesandbox.utils.ProcessUtils;

public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        // String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse：" + executeCodeResponse);
    }

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
        // 3.执行代码
        ArrayList<ExecuteMessage> messageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage runExecuteMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                // ExecuteMessage runExecuteMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
                messageArrayList.add(runExecuteMessage);
                System.out.println(runExecuteMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }
        // 4.收集整理输出结果
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
        // 5.文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println(del ? "删除成功" : "删除失败");
        }
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
