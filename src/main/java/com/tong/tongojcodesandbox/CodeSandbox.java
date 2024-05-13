package com.tong.tongojcodesandbox;

import com.tong.tongojcodesandbox.model.ExecuteCodeRequest;
import com.tong.tongojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 */
public interface CodeSandbox {

    /**
     * 执行代码
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
