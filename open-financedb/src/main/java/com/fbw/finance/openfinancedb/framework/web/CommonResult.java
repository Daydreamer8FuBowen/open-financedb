package com.fbw.finance.openfinancedb.framework.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResult<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(0, "success", data);
    }

    public static <T> CommonResult<T> error(Integer code, String message) {
        return new CommonResult<>(code, message, null);
    }
}
