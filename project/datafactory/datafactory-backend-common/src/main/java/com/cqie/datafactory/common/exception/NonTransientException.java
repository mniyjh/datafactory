package com.cqie.datafactory.common.exception;

/**
 * 不可重试异常 — 永久性故障（SQL语法错误、权限不足、数据不存在）
 * 重试无效，应直接失败
 */
public class NonTransientException extends RuntimeException {
    public NonTransientException(String message) {
        super(message);
    }

    public NonTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
