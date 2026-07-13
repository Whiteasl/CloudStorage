package com.cloudstorage.exception;

public class TokenExpiredException extends RuntimeException {
    // 令牌过期异常
    public TokenExpiredException(String msg){
        super(msg);
    }
}
