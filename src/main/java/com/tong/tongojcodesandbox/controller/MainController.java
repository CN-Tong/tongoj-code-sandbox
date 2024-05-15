package com.tong.tongojcodesandbox.controller;

import com.tong.tongojcodesandbox.JavaDockerCodeSandbox;
import com.tong.tongojcodesandbox.JavaNativeCodeSandbox;
import com.tong.tongojcodesandbox.model.ExecuteCodeRequest;
import com.tong.tongojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("/")
public class MainController {

    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;

    /**
     * 执行代码（Java原生实现）
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode/native")
    ExecuteCodeResponse executeCodeByNative(@RequestBody ExecuteCodeRequest executeCodeRequest){
        if(executeCodeRequest == null){
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }

    /**
     * 执行代码（Docker实现）
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode/docker")
    ExecuteCodeResponse executeCodeByDocker(@RequestBody ExecuteCodeRequest executeCodeRequest){
        if(executeCodeRequest == null){
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }
}
