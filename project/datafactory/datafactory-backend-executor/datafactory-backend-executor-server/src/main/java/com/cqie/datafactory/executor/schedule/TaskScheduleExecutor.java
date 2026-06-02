package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskScheduleExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduleExecutor.class);

    private final ScheduleJobService scheduleJobService;
    private final ExecutorTaskService executorTaskService;

    public TaskScheduleExecutor(ScheduleJobService scheduleJobService,
                                ExecutorTaskService executorTaskService) {
        this.scheduleJobService = scheduleJobService;
        this.executorTaskService = executorTaskService;
    }

    @Scheduled(fixedRate = 30000)
    public void checkAndFire() {
        List<ScheduleJob> jobs = scheduleJobService.listEnabledJobs();
        for (ScheduleJob job : jobs) {
            LocalDateTime nextFire = job.getNextFireTime();
            if (nextFire != null && nextFire.isBefore(LocalDateTime.now())) {
                fireJob(job);
            }
        }
    }

    private void fireJob(ScheduleJob job) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("versionId", job.getTaskVersionId());
            String executionId = executorTaskService.execute(
                    job.getTaskId(), params, job.getEnvironment(), "CRON", job.getId());

            job.setLastExecutionId(executionId);
            job.setLastFireTime(LocalDateTime.now());
            scheduleJobService.computeNextFireTime(job);
            scheduleJobService.updateById(job);
        } catch (Exception e) {
            log.warn("定时任务执行失败: jobId={}, taskId={}", job.getId(), job.getTaskId(), e);
        }
    }
}
