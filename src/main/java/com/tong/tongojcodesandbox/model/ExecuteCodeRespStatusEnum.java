package com.tong.tongojcodesandbox.model;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 程序执行结果状态枚举
 * @author Tong
 */
public enum ExecuteCodeRespStatusEnum {

    SUCCESS("执行成功", 1),
    COMPILE_ERROR("编译错误", 2),
    RUN_ERROR("运行错误", 3);

    private final String text;

    private final Integer value;

    ExecuteCodeRespStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static ExecuteCodeRespStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ExecuteCodeRespStatusEnum anEnum : ExecuteCodeRespStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
