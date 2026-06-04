package com.cqie.datafactory.executor.schedule.guard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.mapper.ExecutionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 三层执行防护：内存 CAS + DB 查重 + 超时驱逐。
 * Layer 1: 内存 CAS（最快，同 JVM）
 * Layer 2: DB 查询（跨实例 + 跨重启）
 * Layer 3: 超时强制驱逐（兜底，防止锁死）
 */
@Component
public class ExecutionGuard {

    private static final Logger log = LoggerFactory.getLogger(ExecutionGuard.class);

    private final ExecutionLogMapper executionLogMapper;
    private final ConcurrentHashMap<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> acquireTimestamps = new ConcurrentHashMap<>();

    public ExecutionGuard(ExecutionLogMapper executionLogMapper) {
        this.executionLogMapper = executionLogMapper;
    }

    /**
     * 尝试获取任务执行权限。
     */
    public boolean tryAcquire(Long taskId) {
        // Layer 1: 内存 CAS
        AtomicBoolean flag = runningTasks.computeIfAbsent(taskId, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            log.warn("ExecutionGuard: task {} already running (in-memory), skip", taskId);
            return false;
        }

        // Layer 2: DB 查重
        try {
            Long runningCount = executionLogMapper.selectCount(
                    new LambdaQueryWrapper<ExecutionLog>()
                            .eq(ExecutionLog::getTaskId, taskId)
                            .eq(ExecutionLog::getStatus, "RUNNING"));
            if (runningCount != null && runningCount > 0) {
                log.warn("ExecutionGuard: task {} has {} RUNNING execution(s) in DB, skip", taskId, runningCount);
                flag.set(false);
                return false;
            }
        } catch (Exception e) {
            log.error("ExecutionGuard: DB check failed for task {}, releasing lock", taskId, e);
            flag.set(false);
            return false;
        }

        acquireTimestamps.computeIfAbsent(taskId, k -> new AtomicLong())
                .set(System.currentTimeMillis());
        return true;
    }

    /**
     * 释放任务执行权限。
     */
    public void release(Long taskId) {
        AtomicBoolean flag = runningTasks.get(taskId);
        if (flag != null) {
            flag.set(false);
        }
        AtomicLong ts = acquireTimestamps.get(taskId);
        if (ts != null) {
            ts.set(0);
        }
    }

    /**
     * 强制释放（用于 COVER 策略，取消旧执行）。
     */
    public void forceRelease(Long taskId) {
        log.info("ExecutionGuard: force releasing task {}", taskId);
        release(taskId);
    }

    /**
     * 驱逐超时的执行锁（由 TimeoutGuardScheduler 周期性调用）。
     * 只驱逐超过 timeoutSeconds 的锁，不在 Guard 内部判断具体秒数。
     *
     * @return 被驱逐的 taskId 列表
     */
    public java.util.List<Long> evictStale(long timeoutMs) {
        java.util.List<Long> evicted = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        acquireTimestamps.forEach((taskId, ts) -> {
            long acquired = ts.get();
            if (acquired > 0 && (now - acquired) > timeoutMs) {
                forceRelease(taskId);
                evicted.add(taskId);
            }
        });
        return evicted;
    }

    /**
     * 获取某任务的持有时间（ms），0 表示未持有。
     */
    public long getHoldDurationMs(Long taskId) {
        AtomicLong ts = acquireTimestamps.get(taskId);
        if (ts == null || ts.get() == 0) return 0;
        return System.currentTimeMillis() - ts.get();
    }
}
