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
    private final ConcurrentHashMap<String, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> acquireTimestamps = new ConcurrentHashMap<>();

    public ExecutionGuard(ExecutionLogMapper executionLogMapper) {
        this.executionLogMapper = executionLogMapper;
    }

    // ============ String key API (多对多, 格式: "jobId:taskId") ============

    public boolean tryAcquire(String key) {
        // Layer 1: 内存 CAS
        AtomicBoolean flag = runningTasks.computeIfAbsent(key, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            log.warn("ExecutionGuard: key {} already running (in-memory), skip", key);
            return false;
        }

        // Layer 2: DB 查重 — 从 key 提取 taskId
        Long taskId = extractTaskId(key);
        if (taskId != null) {
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
                log.error("ExecutionGuard: DB check failed for key {}, releasing lock", key, e);
                flag.set(false);
                return false;
            }
        }

        acquireTimestamps.computeIfAbsent(key, k -> new AtomicLong())
                .set(System.currentTimeMillis());
        return true;
    }

    public void release(String key) {
        AtomicBoolean flag = runningTasks.get(key);
        if (flag != null) {
            flag.set(false);
        }
        AtomicLong ts = acquireTimestamps.get(key);
        if (ts != null) {
            ts.set(0);
        }
    }

    public void forceRelease(String key) {
        log.info("ExecutionGuard: force releasing key {}", key);
        release(key);
    }

    /**
     * 驱逐超时的执行锁。
     * @return 被驱逐的 key 列表
     */
    public java.util.List<String> evictStale(long timeoutMs) {
        java.util.List<String> evicted = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        acquireTimestamps.forEach((key, ts) -> {
            long acquired = ts.get();
            if (acquired > 0 && (now - acquired) > timeoutMs) {
                forceRelease(key);
                evicted.add(key);
            }
        });
        return evicted;
    }

    public long getHoldDurationMs(String key) {
        AtomicLong ts = acquireTimestamps.get(key);
        if (ts == null || ts.get() == 0) return 0;
        return System.currentTimeMillis() - ts.get();
    }

    // ============ 旧 Long 签名 (向后兼容) ============

    public boolean tryAcquire(Long taskId) {
        return tryAcquire(String.valueOf(taskId));
    }

    public void release(Long taskId) {
        release(String.valueOf(taskId));
    }

    public void forceRelease(Long taskId) {
        forceRelease(String.valueOf(taskId));
    }

    // ============ 辅助 ============

    /**
     * 从复合 key "jobId:taskId" 中提取 taskId。
     */
    private Long extractTaskId(String key) {
        if (key == null) return null;
        int idx = key.lastIndexOf(':');
        String taskIdStr = idx >= 0 ? key.substring(idx + 1) : key;
        try {
            return Long.parseLong(taskIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
