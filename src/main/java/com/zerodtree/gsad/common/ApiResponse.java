package com.zerodtree.gsad.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("", "ok", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.name(), message, null);
    }
}
