package com.cqie.datafactory.executor.schedule.listener;

import com.cqie.datafactory.executor.schedule.ScheduleJobService;
import com.cqie.datafactory.executor.schedule.TaskScheduleExecutor;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.event.JobSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务依赖监听器。当父任务执行成功后，异步触发所有子任务。
 */
@Component
public class JobDependencyListener {

    private static final Logger log = LoggerFactory.getLogger(JobDependencyListener.class);

    private final ScheduleJobService scheduleJobService;
    private final TaskScheduleExecutor taskScheduleExecutor;

    public JobDependencyListener(ScheduleJobService scheduleJobService,
                                 TaskScheduleExecutor taskScheduleExecutor) {
        this.scheduleJobService = scheduleJobService;
        this.taskScheduleExecutor = taskScheduleExecutor;
    }

    @Async
    @EventListener
    public void onJobSuccess(JobSuccessEvent event) {
        List<ScheduleJob> childJobs = scheduleJobService.listChildJobs(event.getJobId());
        if (childJobs == null || childJobs.isEmpty()) return;

        log.info("父任务 {} 执行成功，触发 {} 个子任务", event.getJobId(), childJobs.size());
        for (ScheduleJob child : childJobs) {
            if (child.getStatus() == null || child.getStatus() != 1) {
                log.info("子任务 {} 已禁用，跳过", child.getId());
                continue;
            }
            try {
                taskScheduleExecutor.fireJob(child);
                log.info("子任务 {} ({}) 已触发", child.getId(), child.getJobCode());
            } catch (Exception e) {
                log.error("子任务 {} 触发失败: {}", child.getId(), e.getMessage());
            }
        }
    }
}
