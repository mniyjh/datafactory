package com.cqie.datafactory.executor.schedule.listener;

import com.cqie.datafactory.executor.schedule.ScheduleJobService;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.event.JobFailureEvent;
import com.cqie.datafactory.executor.schedule.event.JobTimeoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务告警监听器，异步消费失败/超时事件。
 * 当前实现：日志输出 + 邮件告警信息打印（可替换为真实邮件/企业微信/钉钉 SDK）。
 */
@Component
public class JobAlarmListener {

    private static final Logger log = LoggerFactory.getLogger(JobAlarmListener.class);

    private final ScheduleJobService scheduleJobService;

    public JobAlarmListener(ScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }

    @Async
    @EventListener
    public void onJobFailure(JobFailureEvent event) {
        ScheduleJob job = scheduleJobService.getById(event.getJobId());
        if (job == null) return;
        if (job.getAlarmOnFailure() == null || job.getAlarmOnFailure() == 0) return;

        String alarmMsg = buildFailureMessage(event, job);
        log.warn("[ALARM] 任务执行失败: {}", alarmMsg);

        // 发送邮件告警（当前仅打印，可替换为 JavaMailSender）
        String email = job.getAlarmEmail();
        if (email != null && !email.isBlank()) {
            log.info("[ALARM] 发送告警邮件至: {} | 内容: {}", email, alarmMsg);
            // TODO: sendEmail(email, "DataFactory任务失败告警", alarmMsg);
        }
    }

    @Async
    @EventListener
    public void onJobTimeout(JobTimeoutEvent event) {
        ScheduleJob job = scheduleJobService.getById(event.getJobId());
        if (job == null) return;
        if (job.getAlarmOnTimeout() == null || job.getAlarmOnTimeout() == 0) return;

        String alarmMsg = buildTimeoutMessage(event, job);
        log.warn("[ALARM] 任务执行超时: {}", alarmMsg);

        String email = job.getAlarmEmail();
        if (email != null && !email.isBlank()) {
            log.info("[ALARM] 发送告警邮件至: {} | 内容: {}", email, alarmMsg);
            // TODO: sendEmail(email, "DataFactory任务超时告警", alarmMsg);
        }
    }

    private String buildFailureMessage(JobFailureEvent event, ScheduleJob job) {
        return String.format(
                "调度=%s, 任务=%s(env=%s), 执行ID=%s, 重试已耗尽, 错误=%s, 触发时间=%s",
                job.getJobCode(), event.getTaskName(), event.getEnvironment(),
                event.getExecutionId(), event.getErrorMessage(), event.getFireTime());
    }

    private String buildTimeoutMessage(JobTimeoutEvent event, ScheduleJob job) {
        return String.format(
                "调度=%s, 任务=%s(env=%s), 执行ID=%s, 超时阈值=%ds, 开始时间=%s",
                job.getJobCode(), event.getTaskName(), event.getEnvironment(),
                event.getExecutionId(), event.getTimeoutSeconds(), event.getStartTime());
    }
}
