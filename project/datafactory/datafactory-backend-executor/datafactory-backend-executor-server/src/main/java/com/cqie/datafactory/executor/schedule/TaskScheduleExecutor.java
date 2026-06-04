package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.event.JobFailureEvent;
import com.cqie.datafactory.executor.schedule.guard.ExecutionGuard;
import com.cqie.datafactory.executor.schedule.util.CronHelper;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准调度执行器（分钟级及以上 Cron）。
 * 增强功能: 失败重试、错过触发策略、时间窗口、并发策略、分布式锁。
 */
@Component
public class TaskScheduleExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduleExecutor.class);

    private final ScheduleJobService scheduleJobService;
    private final ExecutorTaskService executorTaskService;
    private final ExecutionGuard executionGuard;
    private final TaskExecutionQueue taskExecutionQueue;
    private final DistributedScheduleLock distributedLock;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskScheduleExecutor(ScheduleJobService scheduleJobService,
                                ExecutorTaskService executorTaskService,
                                ExecutionGuard executionGuard,
                                TaskExecutionQueue taskExecutionQueue,
                                DistributedScheduleLock distributedLock,
                                ApplicationEventPublisher eventPublisher) {
        this.scheduleJobService = scheduleJobService;
        this.executorTaskService = executorTaskService;
        this.executionGuard = executionGuard;
        this.taskExecutionQueue = taskExecutionQueue;
        this.distributedLock = distributedLock;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRate = 30000)
    public void checkAndFire() {
        List<ScheduleJob> jobs = scheduleJobService.listEnabledJobs();
        LocalDateTime now = LocalDateTime.now();

        for (ScheduleJob job : jobs) {
            // 高频任务由 HighFrequencyScheduler 管理
            if (CronHelper.isHighFrequency(job.getCronExpression())) {
                continue;
            }

            LocalDateTime nextFire = job.getNextFireTime();
            if (nextFire == null || !nextFire.isBefore(now)) {
                continue;
            }

            // 时间窗口检查
            if (!isWithinTimeWindow(job)) {
                scheduleJobService.computeNextFireTime(job);
                scheduleJobService.updateFireResult(job);
                continue;
            }

            // 错过触发策略
            String misfire = job.getMisfireStrategy() != null ? job.getMisfireStrategy() : "IGNORE";
            if ("FIRE_ALL".equals(misfire)) {
                List<LocalDateTime> missed = computeMissedFires(job.getCronExpression(), nextFire, now);
                for (int i = 0; i < missed.size(); i++) {
                    fireJob(job);
                }
            } else {
                // IGNORE 或 FIRE_ONCE 都只触发一次
                fireJob(job);
            }
        }
    }

    /**
     * 触发单个调度任务（含重试、并发策略、分布式锁）。
     */
    public void fireJob(ScheduleJob job) {
        switch (job.getBlockStrategy() != null ? job.getBlockStrategy() : "SKIP") {
            case "QUEUE" -> fireWithQueue(job);
            case "COVER" -> fireWithCover(job);
            default -> fireWithSkip(job);
        }
    }

    // ===================== Block Strategies =====================

    private void fireWithSkip(ScheduleJob job) {
        if (!executionGuard.tryAcquire(job.getTaskId())) {
            log.debug("SKIP: task {} 正在执行中，跳过本次触发", job.getTaskId());
            return;
        }
        try {
            executeWithRetry(job);
        } finally {
            executionGuard.release(job.getTaskId());
        }
    }

    private void fireWithQueue(ScheduleJob job) {
        if (!executionGuard.tryAcquire(job.getTaskId())) {
            int maxSize = job.getMaxQueueSize() != null ? job.getMaxQueueSize() : 5;
            boolean queued = taskExecutionQueue.enqueue(job, maxSize);
            if (!queued) {
                log.warn("QUEUE: job {} 队列已满，丢弃", job.getId());
            }
            return;
        }
        try {
            executeWithRetry(job);
            // 执行完成后，处理排队中的任务
            drainQueue(job.getId());
        } finally {
            executionGuard.release(job.getTaskId());
        }
    }

    private void fireWithCover(ScheduleJob job) {
        executionGuard.forceRelease(job.getTaskId());
        executionGuard.tryAcquire(job.getTaskId());
        try {
            executeWithRetry(job);
        } finally {
            executionGuard.release(job.getTaskId());
        }
    }

    private void drainQueue(Long jobId) {
        ScheduleJob queued = taskExecutionQueue.dequeue(jobId);
        while (queued != null) {
            log.info("QUEUE: 从队列取出 job {} 开始执行", jobId);
            fireWithSkip(queued);
            queued = taskExecutionQueue.dequeue(jobId);
        }
    }

    // ===================== Retry Logic =====================

    private void executeWithRetry(ScheduleJob job) {
        // 获取分布式锁（多实例互斥）
        int lockSec = computeLockSeconds(job);
        if (!distributedLock.tryLock(job.getId(), lockSec)) {
            log.debug("分布式锁: job {} 被其他实例持有，跳过", job.getId());
            return;
        }

        int maxRetries = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int retryInterval = job.getRetryInterval() != null ? job.getRetryInterval() : 60;
        Exception lastException = null;

        try {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (attempt > 0) {
                        // 重试前重新获取最新状态
                        ScheduleJob latest = scheduleJobService.getById(job.getId());
                        if (latest == null || latest.getStatus() == null || latest.getStatus() != 1) {
                            log.info("重试中止: job {} 已被禁用或删除", job.getId());
                            return;
                        }
                        Thread.sleep(retryInterval * 1000L);
                    }
                    doFire(job, attempt);
                    job.setCurrentRetry(0);
                    return; // 成功
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    lastException = e;
                    job.setCurrentRetry(attempt + 1);
                    scheduleJobService.updateFireResult(job);
                    log.warn("任务执行失败，第{}/{}次重试: jobId={}, error={}",
                            attempt + 1, maxRetries, job.getId(), e.getMessage());
                }
            }

            // 重试耗尽
            log.error("任务最终失败: jobId={}, 已重试{}次", job.getId(), maxRetries);
            eventPublisher.publishEvent(new JobFailureEvent(
                    job.getId(), job.getTaskId(),
                    "task-" + job.getTaskId(), // taskName 可从 task 表查询优化
                    job.getLastExecutionId(),
                    lastException != null ? lastException.getMessage() : "unknown",
                    job.getEnvironment(), LocalDateTime.now()));

        } finally {
            distributedLock.release(job.getId());
        }
    }

    private void doFire(ScheduleJob job, int attempt) {
        Map<String, Object> params = new HashMap<>();
        params.put("versionId", job.getTaskVersionId());
        if (attempt > 0) {
            params.put("retryAttempt", attempt);
        }

        // 解析定时任务参数配置，将覆盖值传入执行引擎
        if (job.getParamsConfig() != null && !job.getParamsConfig().isBlank()) {
            try {
                Map<String, Object> configMap = objectMapper.readValue(
                        job.getParamsConfig(), new TypeReference<Map<String, Object>>() {});
                // paramsConfig 结构: { "params": [{ "paramCode": "...", "sourceValue": "...", ... }, ...] }
                // 提取 paramCode -> sourceValue 的映射，注入到执行参数中
                Object paramsList = configMap.get("params");
                if (paramsList instanceof List) {
                    for (Object item : (List<?>) paramsList) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> paramItem = (Map<String, Object>) item;
                            String paramCode = (String) paramItem.get("paramCode");
                            Object sourceValue = paramItem.get("sourceValue");
                            if (paramCode != null && !paramCode.isBlank() && sourceValue != null) {
                                params.put(paramCode, sourceValue);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析定时任务参数配置失败: jobId={}, error={}", job.getId(), e.getMessage());
            }
        }

        String triggerType = (attempt > 0) ? "CRON_RETRY" : "CRON";
        String executionId = executorTaskService.execute(
                job.getTaskId(), params, job.getEnvironment(), triggerType, job.getId());

        job.setLastExecutionId(executionId);
        job.setLastFireTime(LocalDateTime.now());
        scheduleJobService.computeNextFireTime(job);
        scheduleJobService.updateFireResult(job);

        log.info("定时任务触发成功: jobId={}, taskId={}, executionId={}, attempt={}",
                job.getId(), job.getTaskId(), executionId, attempt);
    }

    // ===================== Helpers =====================

    /**
     * 检查当前时间是否在配置的时间窗口内。
     */
    private boolean isWithinTimeWindow(ScheduleJob job) {
        if (job.getWindowStart() == null && job.getWindowEnd() == null) {
            return true;
        }
        LocalTime now = LocalTime.now();
        if (job.getWindowStart() != null && now.isBefore(job.getWindowStart())) {
            log.debug("job {} 不在时间窗口内 (当前时间 {} < 窗口开始 {})",
                    job.getId(), now, job.getWindowStart());
            return false;
        }
        if (job.getWindowEnd() != null && now.isAfter(job.getWindowEnd())) {
            log.debug("job {} 不在时间窗口内 (当前时间 {} > 窗口结束 {})",
                    job.getId(), now, job.getWindowEnd());
            return false;
        }
        return true;
    }

    /**
     * 计算错过触发的所有时间点。
     */
    private List<LocalDateTime> computeMissedFires(String cronExpr, LocalDateTime lastPlanned, LocalDateTime now) {
        List<LocalDateTime> fires = new ArrayList<>();
        org.springframework.scheduling.support.CronExpression cron =
                org.springframework.scheduling.support.CronExpression.parse(cronExpr);
        LocalDateTime next = cron.next(lastPlanned);
        int maxCatchUp = 10;
        int count = 0;
        while (next != null && next.isBefore(now) && count < maxCatchUp) {
            fires.add(next);
            next = cron.next(next);
            count++;
        }
        if (count >= maxCatchUp) {
            log.warn("错过触发次数超过上限 {}，仅追回最近 {} 次", maxCatchUp, maxCatchUp);
        }
        return fires;
    }

    /**
     * 计算分布式锁持有秒数：executor_timeout + retry 开销估算。
     */
    private int computeLockSeconds(ScheduleJob job) {
        int baseTimeout = job.getExecutorTimeout() != null ? job.getExecutorTimeout() : 300;
        if (baseTimeout <= 0) baseTimeout = 300;
        int retries = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int retryInterval = job.getRetryInterval() != null ? job.getRetryInterval() : 60;
        return baseTimeout + (retries * (baseTimeout + retryInterval)) + 30; // +30s 缓冲
    }
}
