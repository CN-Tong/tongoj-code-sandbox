package com.tong.tongojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码沙箱执行代码的请求封装类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {

    /**
     * 一组输入用例
     */
    private List<String> inputList;

    /**
     * 提交的代码
     */
    private String code;

    /**
     * 编程语言
     */
    private String language;
}
