package com.cqie.datafactory.executor.util;

import com.cqie.datafactory.common.exception.NonTransientException;
import com.cqie.datafactory.common.exception.TransientException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避重试工具
 * 公式: delay = min(baseDelay * 2^attempt, maxDelay) + random(0, jitter)
 */
@Slf4j
public final class RetryUtil {

    // 可重试的异常类型（连接失败、超时、IO异常等）
    private static final Class<?>[] RETRYABLE = {
            java.net.SocketTimeoutException.class,
            java.net.ConnectException.class,
            java.sql.SQLTimeoutException.class,
            java.sql.SQLTransientConnectionException.class,
            java.util.concurrent.TimeoutException.class,
            java.io.IOException.class,
            TransientException.class
    };

    private RetryUtil() {}

    /**
     * 判断是否应该重试
     */
    public static boolean isRetryable(Throwable e) {
        if (e instanceof NonTransientException) {
            return false;
        }
        for (Class<?> clazz : RETRYABLE) {
            if (clazz.isInstance(e)) {
                return true;
            }
        }
        // 兜底: RuntimeException 可重试, Error 不可重试
        return e instanceof RuntimeException;
    }

    /**
     * 执行带指数退避的重试
     *
     * @param callable      要执行的操作
     * @param maxRetries    最大重试次数
     * @param baseDelayMs   基础延迟(毫秒)
     * @param maxDelayMs    最大延迟上限(毫秒)
     * @param jitterMs      抖动范围(毫秒)
     */
    public static <T> T executeWithRetry(
            Callable<T> callable,
            int maxRetries,
            long baseDelayMs,
            long maxDelayMs,
            long jitterMs) throws Exception {

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return callable.call();
            } catch (Exception e) {
                lastException = e;

                if (attempt >= maxRetries || !isRetryable(e)) {
                    throw e;
                }

                long delay = Math.min(baseDelayMs * (1L << attempt), maxDelayMs);
                long jitter = ThreadLocalRandom.current().nextLong(jitterMs + 1);
                long sleepMs = delay + jitter;

                log.warn("Retry {}/{} after {}ms for: {}",
                        attempt + 1, maxRetries, sleepMs, e.getMessage());

                Thread.sleep(sleepMs);
            }
        }

        throw lastException;
    }

    /**
     * 简化版: 最大3次重试, 1s基础延迟, 30s上限, 500ms抖动
     */
    public static <T> T executeWithRetry(Callable<T> callable) throws Exception {
        return executeWithRetry(callable, 3, 1000, 30000, 500);
    }
}
