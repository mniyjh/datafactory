package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.event.JobTimeoutEvent;
import com.cqie.datafactory.executor.schedule.guard.ExecutionGuard;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 周期性检查执行超时任务，强制标记超时并释放锁。
 */
@Component
public class TimeoutGuardScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutGuardScheduler.class);

    private final ExecutionGuard executionGuard;
    private final ExecutionLogService executionLogService;
    private final ScheduleJobService scheduleJobService;
    private final ApplicationEventPublisher eventPublisher;

    public TimeoutGuardScheduler(ExecutionGuard executionGuard,
                                  ExecutionLogService executionLogService,
                                  ScheduleJobService scheduleJobService,
                                  ApplicationEventPublisher eventPublisher) {
        this.executionGuard = executionGuard;
        this.executionLogService = executionLogService;
        this.scheduleJobService = scheduleJobService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRate = 15000)
    public void checkTimeoutExecutions() {
        List<ExecutionLog> runningLogs = executionLogService.list(
                new LambdaQueryWrapper<ExecutionLog>()
                        .eq(ExecutionLog::getStatus, "RUNNING"));

        for (ExecutionLog execLog : runningLogs) {
            if (execLog.getScheduleJobId() == null) continue;

            ScheduleJob job = scheduleJobService.getById(execLog.getScheduleJobId());
            if (job == null) continue;

            int timeoutSec = job.getExecutorTimeout() != null ? job.getExecutorTimeout() : 0;
            if (timeoutSec <= 0) continue;

            long runningMs = Duration.between(execLog.getStartTime(), LocalDateTime.now()).toMillis();
            if (runningMs > timeoutSec * 1000L) {
                log.warn("执行超时: executionId={}, taskId={}, jobId={}, runningMs={}, timeoutSec={}",
                        execLog.getExecutionId(), execLog.getTaskId(), job.getId(), runningMs, timeoutSec);

                // 标记执行日志为超时
                execLog.setStatus("TIMEOUT");
                execLog.setEndTime(LocalDateTime.now());
                execLog.setDurationMs(runningMs);
                execLog.setErrorMessage("执行超时 (阈值: " + timeoutSec + "s, 实际: " + runningMs + "ms)");
                executionLogService.updateById(execLog);

                // 释放执行锁 (使用复合 key)
                executionGuard.release(execLog.getScheduleJobId() + ":" + execLog.getTaskId());
                executionGuard.release(execLog.getTaskId()); // 兼容旧 Long key

                // 发布超时事件
                eventPublisher.publishEvent(new JobTimeoutEvent(
                        job.getId(), execLog.getTaskId(), execLog.getTaskName(),
                        execLog.getExecutionId(), execLog.getEnvironment(),
                        timeoutSec, execLog.getStartTime()));

                // 清理当前重试计数
                job.setCurrentRetry(0);
                scheduleJobService.updateFireResult(job);
            }
        }
    }
}
