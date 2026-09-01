package com.zerodtree.gsad.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        this(errorCode, message, data, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = data;
    }
}
