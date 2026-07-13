package com.cloudstorage.exception;

public class InvalidTokenException extends RuntimeException {
    // 无效令牌异常
    public InvalidTokenException(String message) {
        super(message);
    }
}
