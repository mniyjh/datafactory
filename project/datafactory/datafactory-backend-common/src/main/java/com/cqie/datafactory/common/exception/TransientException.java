package com.cqie.datafactory.common.exception;

/**
 * 可重试异常 — 临时性故障（连接超时、网络抖动、资源暂时不可用）
 * 重试后大概率恢复
 */
public class TransientException extends RuntimeException {
    public TransientException(String message) {
        super(message);
    }

    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
