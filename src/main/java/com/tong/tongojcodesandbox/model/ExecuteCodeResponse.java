package com.tong.tongojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码沙箱执行代码的响应封装类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {

    /**
     * 一组输出用例
     */
    private List<String> outputList;

    /**
     * 接口信息
     */
    private String message;

    /**
     * 执行状态
     */
    private Integer status;

    /**
     * 编程语言
     */
    private JudgeInfo judgeInfo;
}
