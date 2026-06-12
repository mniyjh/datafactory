package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.common.context.TenantContext;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobTask;
import com.cqie.datafactory.executor.schedule.event.JobFailureEvent;
import com.cqie.datafactory.executor.schedule.event.JobSuccessEvent;
import com.cqie.datafactory.executor.schedule.guard.ExecutionGuard;
import com.cqie.datafactory.executor.schedule.util.CronHelper;
import com.cqie.datafactory.executor.service.ExecutorInstanceService;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
    private final ExecutorInstanceService executorInstanceService;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${datafactory.executor.instance-id:}")
    private String instanceId;

    public TaskScheduleExecutor(ScheduleJobService scheduleJobService,
                                ExecutorTaskService executorTaskService,
                                ExecutionGuard executionGuard,
                                TaskExecutionQueue taskExecutionQueue,
                                DistributedScheduleLock distributedLock,
                                ApplicationEventPublisher eventPublisher,
                                ExecutorInstanceService executorInstanceService,
                                Environment environment) {
        this.scheduleJobService = scheduleJobService;
        this.executorTaskService = executorTaskService;
        this.executionGuard = executionGuard;
        this.taskExecutionQueue = taskExecutionQueue;
        this.distributedLock = distributedLock;
        this.eventPublisher = eventPublisher;
        this.executorInstanceService = executorInstanceService;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        if (instanceId != null && !instanceId.isBlank()) {
            String port = environment.getProperty("server.port", "8082");
            executorInstanceService.register(instanceId, Integer.parseInt(port));
            log.info("Executor HA initialized: instance={}, online={}, slot={}",
                    instanceId,
                    executorInstanceService.getOnlineCount(),
                    executorInstanceService.getMySlotIndex(instanceId));
        }
    }

    @PreDestroy
    public void destroy() {
        if (instanceId != null && !instanceId.isBlank()) {
            executorInstanceService.shutdown(instanceId);
        }
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

            // 槽位分片：只处理分配给当前实例的作业
            if (instanceId != null && !instanceId.isBlank()
                    && !executorInstanceService.shouldHandle(instanceId, job.getId())) {
                log.debug("Job {} assigned to another executor, skipping", job.getId());
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
        scheduleJobService.loadJobTasks(job);
        List<ScheduleJobTask> tasks = job.getJobTasks();
        if (tasks == null || tasks.isEmpty()) {
            log.warn("Job {} 没有关联任务，跳过", job.getId());
            return;
        }
        for (ScheduleJobTask link : tasks) {
            String lockKey = buildTaskLockKey(job.getId(), link);
            if (!executionGuard.tryAcquire(lockKey)) {
                log.debug("SKIP: task {} 正在执行中 (jobId={}), 跳过", link.getTaskId(), job.getId());
                continue;
            }
            try {
                executeSingleWithRetry(job, link);
            } finally {
                executionGuard.release(lockKey);
            }
        }
    }

    private void fireWithQueue(ScheduleJob job) {
        scheduleJobService.loadJobTasks(job);
        List<ScheduleJobTask> tasks = job.getJobTasks();
        if (tasks == null || tasks.isEmpty()) return;
        for (ScheduleJobTask link : tasks) {
            String lockKey = buildTaskLockKey(job.getId(), link);
            if (!executionGuard.tryAcquire(lockKey)) {
                int maxSize = job.getMaxQueueSize() != null ? job.getMaxQueueSize() : 5;
                boolean queued = taskExecutionQueue.enqueue(job, maxSize);
                if (!queued) {
                    log.warn("QUEUE: job {} 队列已满，丢弃", job.getId());
                }
                continue;
            }
            try {
                executeSingleWithRetry(job, link);
                // 执行完成后，处理排队中的任务
                drainQueue(job.getId());
            } finally {
                executionGuard.release(lockKey);
            }
        }
    }

    private void fireWithCover(ScheduleJob job) {
        scheduleJobService.loadJobTasks(job);
        List<ScheduleJobTask> tasks = job.getJobTasks();
        if (tasks == null || tasks.isEmpty()) return;
        for (ScheduleJobTask link : tasks) {
            String lockKey = buildTaskLockKey(job.getId(), link);
            executionGuard.forceRelease(lockKey);
            executionGuard.tryAcquire(lockKey);
            try {
                executeSingleWithRetry(job, link);
            } finally {
                executionGuard.release(lockKey);
            }
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

    /**
     * 对单个 task 执行并重试。
     */
    private void executeSingleWithRetry(ScheduleJob job, ScheduleJobTask link) {
        int maxRetries = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int retryInterval = job.getRetryInterval() != null ? job.getRetryInterval() : 60;
        Exception lastException = null;

        // 设置租户上下文
        if (job.getTenantId() != null) {
            TenantContext.set(job.getTenantId());
        }

        // 获取分布式锁（多实例互斥）
        int lockSec = computeLockSeconds(job);
        if (!distributedLock.tryLock(job.getId(), lockSec)) {
            log.debug("分布式锁: job {} 被其他实例持有，跳过", job.getId());
            TenantContext.clear();
            return;
        }

        try {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (attempt > 0) {
                        ScheduleJob latest = scheduleJobService.getById(job.getId());
                        if (latest == null || latest.getStatus() == null || latest.getStatus() != 1) {
                            log.info("重试中止: job {} 已被禁用或删除", job.getId());
                            return;
                        }
                        // 使用指数退避重试
                        long delay = Math.min(retryInterval * 1000L * (1L << (attempt - 1)), 60000L);
                        long jitter = ThreadLocalRandom.current().nextLong(1001);
                        Thread.sleep(delay + jitter);
                    }
                    doFireTask(job, link, attempt);
                    job.setCurrentRetry(0);
                    eventPublisher.publishEvent(new JobSuccessEvent(
                            job.getId(), job.getEnvironment(), LocalDateTime.now()));
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    lastException = e;
                    job.setCurrentRetry(attempt + 1);
                    scheduleJobService.updateFireResult(job);
                    log.warn("任务执行失败，第{}/{}次重试: jobId={}, taskId={}, error={}",
                            attempt + 1, maxRetries, job.getId(), link.getTaskId(), e.getMessage());
                }
            }

            log.error("任务最终失败: jobId={}, taskId={}, 已重试{}次", job.getId(), link.getTaskId(), maxRetries);
            eventPublisher.publishEvent(new JobFailureEvent(
                    job.getId(), link.getTaskId(),
                    "task-" + link.getTaskId(),
                    job.getLastExecutionId(),
                    lastException != null ? lastException.getMessage() : "unknown",
                    job.getEnvironment(), LocalDateTime.now()));
        } finally {
            distributedLock.release(job.getId());
            TenantContext.clear();
        }
    }

    /**
     * 触发单个任务的执行。
     */
    private void doFireTask(ScheduleJob job, ScheduleJobTask link, int attempt) {
        Map<String, Object> params = new HashMap<>();
        params.put("versionId", link.getTaskVersionId());
        if (attempt > 0) {
            params.put("retryAttempt", attempt);
        }

        // 解析每个任务的 paramsConfig (优先) 或 job 级别的 paramsConfig (回退)
        String configJson = (link.getParamsConfig() != null && !link.getParamsConfig().isBlank())
                ? link.getParamsConfig() : job.getParamsConfig();
        if (configJson != null && !configJson.isBlank()) {
            mergeParamsFromConfig(params, configJson);
        }

        String triggerType = (attempt > 0) ? "CRON_RETRY" : "CRON";
        String executionId = executorTaskService.execute(
                link.getTaskId(), params,
                resolveTaskEnv(link, job), triggerType, job.getId());

        job.setLastExecutionId(executionId);
        job.setLastFireTime(LocalDateTime.now());
        scheduleJobService.computeNextFireTime(job);
        scheduleJobService.updateFireResult(job);

        log.info("定时任务触发成功: jobId={}, taskId={}, executionId={}, attempt={}",
                job.getId(), link.getTaskId(), executionId, attempt);
    }

    private void mergeParamsFromConfig(Map<String, Object> params, String configJson) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(
                    configJson, new TypeReference<Map<String, Object>>() {});
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
            log.warn("解析参数配置失败: error={}", e.getMessage());
        }
    }

    private String buildLockKey(Long jobId, Long taskId) {
        return jobId + ":" + taskId;
    }

    /** 多任务场景锁键：区分同一 task 的不同版本 */
    private String buildTaskLockKey(Long jobId, ScheduleJobTask link) {
        return jobId + ":" + link.getTaskId() + ":" + link.getTaskVersionId();
    }

    /**
     * 解析任务执行环境: 优先使用 link 级别, 回退到 job 级别。
     */
    private String resolveTaskEnv(ScheduleJobTask link, ScheduleJob job) {
        if (link.getEnvironment() != null && !link.getEnvironment().isBlank()) {
            return link.getEnvironment().trim().toUpperCase();
        }
        return job.getEnvironment();
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
