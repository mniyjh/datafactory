package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.schedule.config.HighFrequencySchedulerProperties;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.event.JobFailureEvent;
import com.cqie.datafactory.executor.schedule.guard.ExecutionGuard;
import com.cqie.datafactory.executor.schedule.util.CronHelper;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 高频（秒级）Cron 调度管理器。
 * 为每个秒级 job 维护独立 ScheduledExecutorService。
 *
 * 增强功能: 失败重试、时间窗口、并发策略、分布式锁。
 */
@Component
public class HighFrequencyScheduler {

    private static final Logger log = LoggerFactory.getLogger(HighFrequencyScheduler.class);

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> jobFutures = new ConcurrentHashMap<>();
    private final ScheduleJobService scheduleJobService;
    private final ExecutorTaskService executorTaskService;
    private final ExecutionGuard executionGuard;
    private final TaskExecutionQueue taskExecutionQueue;
    private final DistributedScheduleLock distributedLock;
    private final ApplicationEventPublisher eventPublisher;
    private final HighFrequencySchedulerProperties properties;

    public HighFrequencyScheduler(ScheduleJobService scheduleJobService,
                                   ExecutorTaskService executorTaskService,
                                   ExecutionGuard executionGuard,
                                   TaskExecutionQueue taskExecutionQueue,
                                   DistributedScheduleLock distributedLock,
                                   ApplicationEventPublisher eventPublisher,
                                   HighFrequencySchedulerProperties properties) {
        this.scheduleJobService = scheduleJobService;
        this.executorTaskService = executorTaskService;
        this.executionGuard = executionGuard;
        this.taskExecutionQueue = taskExecutionQueue;
        this.distributedLock = distributedLock;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.scheduler = Executors.newScheduledThreadPool(properties.getThreadPoolSize(), r -> {
            Thread t = new Thread(r, "hf-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("HighFrequencyScheduler is disabled via configuration");
            return;
        }
        reloadAll();
    }

    @PreDestroy
    public void shutdown() {
        log.info("HighFrequencyScheduler shutting down, cancelling {} jobs", jobFutures.size());
        jobFutures.values().forEach(f -> f.cancel(false));
        jobFutures.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void registerJob(ScheduleJob job) {
        if (!properties.isEnabled()) return;
        if (job.getStatus() == null || job.getStatus() != 1) {
            log.debug("Job {} is disabled, skip registration", job.getId());
            return;
        }
        if (!CronHelper.isHighFrequency(job.getCronExpression())) {
            log.debug("Job {} cron '{}' is not high-frequency, skip", job.getId(), job.getCronExpression());
            return;
        }

        unregisterJob(job.getId());

        long intervalSec = CronHelper.getIntervalSeconds(job.getCronExpression());
        if (intervalSec < properties.getMinIntervalSeconds()) {
            log.warn("Job {} interval {}s below minimum {}s, using minimum",
                    job.getId(), intervalSec, properties.getMinIntervalSeconds());
            intervalSec = properties.getMinIntervalSeconds();
        }

        Runnable fireTask = () -> fireIfNotRunning(job);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                fireTask, 0, intervalSec, TimeUnit.SECONDS);

        jobFutures.put(job.getId(), future);
        log.info("Registered high-frequency job {} (taskId={}) at {}s interval",
                job.getId(), job.getTaskId(), intervalSec);
    }

    public void unregisterJob(Long jobId) {
        ScheduledFuture<?> future = jobFutures.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("Unregistered high-frequency job {}", jobId);
        }
    }

    public void onJobChanged(ScheduleJob job) {
        if (CronHelper.isHighFrequency(job.getCronExpression()) && job.getStatus() != null && job.getStatus() == 1) {
            registerJob(job);
        } else {
            unregisterJob(job.getId());
        }
    }

    public void onJobUpdated(ScheduleJob oldJob, ScheduleJob newJob) {
        unregisterJob(oldJob.getId());
        onJobChanged(newJob);
    }

    public void onJobRemoved(ScheduleJob job) {
        unregisterJob(job.getId());
    }

    public void reloadAll() {
        log.info("Reloading all high-frequency jobs...");
        jobFutures.values().forEach(f -> f.cancel(false));
        jobFutures.clear();

        List<ScheduleJob> jobs = scheduleJobService.listEnabledJobs();
        int count = 0;
        for (ScheduleJob job : jobs) {
            if (CronHelper.isHighFrequency(job.getCronExpression())) {
                registerJob(job);
                count++;
            }
        }
        log.info("Reloaded {} high-frequency jobs", count);
    }

    public boolean isRegistered(Long jobId) {
        ScheduledFuture<?> future = jobFutures.get(jobId);
        return future != null && !future.isCancelled();
    }

    // ===================== Core fire logic =====================

    private void fireIfNotRunning(ScheduleJob job) {
        ScheduleJob latest = scheduleJobService.getById(job.getId());
        if (latest == null || latest.getStatus() == null || latest.getStatus() != 1) {
            log.debug("Job {} is no longer enabled, unregistering", job.getId());
            unregisterJob(job.getId());
            return;
        }

        // 时间窗口检查
        if (!isWithinTimeWindow(latest)) {
            return;
        }

        switch (latest.getBlockStrategy() != null ? latest.getBlockStrategy() : "SKIP") {
            case "QUEUE" -> fireHfWithQueue(latest);
            case "COVER" -> fireHfWithCover(latest);
            default -> fireHfWithSkip(latest);
        }
    }

    private void fireHfWithSkip(ScheduleJob job) {
        if (!executionGuard.tryAcquire(job.getTaskId())) return;
        try {
            executeHfWithRetry(job);
        } finally {
            executionGuard.release(job.getTaskId());
        }
    }

    private void fireHfWithQueue(ScheduleJob job) {
        if (!executionGuard.tryAcquire(job.getTaskId())) {
            int maxSize = job.getMaxQueueSize() != null ? job.getMaxQueueSize() : 5;
            taskExecutionQueue.enqueue(job, maxSize);
            return;
        }
        try {
            executeHfWithRetry(job);
            // 执行排队任务
            ScheduleJob queued = taskExecutionQueue.dequeue(job.getId());
            while (queued != null) {
                fireHfWithSkip(queued);
                queued = taskExecutionQueue.dequeue(job.getId());
            }
        } finally {
            executionGuard.release(job.getTaskId());
        }
    }

    private void fireHfWithCover(ScheduleJob job) {
        executionGuard.forceRelease(job.getTaskId());
        executionGuard.tryAcquire(job.getTaskId());
        try {
            executeHfWithRetry(job);
        } finally {
            executionGuard.release(job.getTaskId());
        }
    }

    private void executeHfWithRetry(ScheduleJob job) {
        int lockSec = computeLockSeconds(job);
        if (!distributedLock.tryLock(job.getId(), lockSec)) return;

        int maxRetries = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int retryInterval = job.getRetryInterval() != null ? job.getRetryInterval() : 60;
        Exception lastException = null;

        try {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (attempt > 0) {
                        ScheduleJob latest = scheduleJobService.getById(job.getId());
                        if (latest == null || latest.getStatus() == null || latest.getStatus() != 1) return;
                        Thread.sleep(retryInterval * 1000L);
                    }
                    doHfFire(job, attempt);
                    job.setCurrentRetry(0);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    lastException = e;
                    job.setCurrentRetry(attempt + 1);
                    scheduleJobService.updateFireResult(job);
                    log.warn("HF任务执行失败，第{}/{}次重试: jobId={}", attempt + 1, maxRetries, job.getId());
                }
            }

            log.error("HF任务最终失败: jobId={}, 已重试{}次", job.getId(), maxRetries);
            eventPublisher.publishEvent(new JobFailureEvent(
                    job.getId(), job.getTaskId(),
                    "task-" + job.getTaskId(),
                    job.getLastExecutionId(),
                    lastException != null ? lastException.getMessage() : "unknown",
                    job.getEnvironment(), LocalDateTime.now()));

        } finally {
            distributedLock.release(job.getId());
        }
    }

    private void doHfFire(ScheduleJob job, int attempt) {
        Map<String, Object> params = new HashMap<>();
        params.put("versionId", job.getTaskVersionId());
        if (attempt > 0) {
            params.put("retryAttempt", attempt);
        }

        String triggerType = (attempt > 0) ? "CRON_RETRY" : "CRON";
        String executionId = executorTaskService.execute(
                job.getTaskId(), params, job.getEnvironment(), triggerType, job.getId());

        job.setLastExecutionId(executionId);
        job.setLastFireTime(LocalDateTime.now());
        scheduleJobService.computeNextFireTime(job);
        scheduleJobService.updateFireResult(job);

        log.debug("Fired high-frequency job {} (taskId={}), executionId={}, attempt={}",
                job.getId(), job.getTaskId(), executionId, attempt);
    }

    private boolean isWithinTimeWindow(ScheduleJob job) {
        if (job.getWindowStart() == null && job.getWindowEnd() == null) return true;
        LocalTime now = LocalTime.now();
        if (job.getWindowStart() != null && now.isBefore(job.getWindowStart())) return false;
        if (job.getWindowEnd() != null && now.isAfter(job.getWindowEnd())) return false;
        return true;
    }

    private int computeLockSeconds(ScheduleJob job) {
        int baseTimeout = job.getExecutorTimeout() != null ? job.getExecutorTimeout() : 300;
        if (baseTimeout <= 0) baseTimeout = 300;
        int retries = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int retryInterval = job.getRetryInterval() != null ? job.getRetryInterval() : 60;
        return baseTimeout + (retries * (baseTimeout + retryInterval)) + 30;
    }
}
