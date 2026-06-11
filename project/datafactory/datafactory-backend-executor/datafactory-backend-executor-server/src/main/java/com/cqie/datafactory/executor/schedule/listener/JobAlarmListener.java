package com.cqie.datafactory.executor.schedule.listener;

import com.cqie.datafactory.executor.schedule.ScheduleJobService;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.event.JobFailureEvent;
import com.cqie.datafactory.executor.schedule.event.JobTimeoutEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 任务告警监听器，异步消费失败/超时事件，通过 QQ 邮箱 SMTP 发送告警邮件。
 */
@Component
public class JobAlarmListener {

    private static final Logger log = LoggerFactory.getLogger(JobAlarmListener.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ScheduleJobService scheduleJobService;
    private final JavaMailSender mailSender;

    public JobAlarmListener(ScheduleJobService scheduleJobService, JavaMailSender mailSender) {
        this.scheduleJobService = scheduleJobService;
        this.mailSender = mailSender;
    }

    @Async
    @EventListener
    public void onJobFailure(JobFailureEvent event) {
        ScheduleJob job = scheduleJobService.getById(event.getJobId());
        if (job == null) return;
        if (job.getAlarmOnFailure() == null || job.getAlarmOnFailure() == 0) return;

        String subject = String.format("[DataFactory] 任务失败告警 — %s", job.getJobCode());
        String body = buildFailureBody(event, job);
        sendMail(job, subject, body);
    }

    @Async
    @EventListener
    public void onJobTimeout(JobTimeoutEvent event) {
        ScheduleJob job = scheduleJobService.getById(event.getJobId());
        if (job == null) return;
        if (job.getAlarmOnTimeout() == null || job.getAlarmOnTimeout() == 0) return;

        String subject = String.format("[DataFactory] 任务超时告警 — %s", job.getJobCode());
        String body = buildTimeoutBody(event, job);
        sendMail(job, subject, body);
    }

    private void sendMail(ScheduleJob job, String subject, String body) {
        String email = job.getAlarmEmail();
        if (email == null || email.isBlank()) {
            log.warn("[ALARM] 任务 {} 未配置告警邮箱，仅记录日志: {}", job.getJobCode(), subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom("1106935659@qq.com");
            helper.setTo(email.split(","));
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(msg);
            log.info("[ALARM] 告警邮件已发送至: {} | 主题: {}", email, subject);
        } catch (MessagingException e) {
            log.error("[ALARM] 告警邮件发送失败: {} | 错误: {}", email, e.getMessage());
        }
    }

    private String buildFailureBody(JobFailureEvent event, ScheduleJob job) {
        return String.format("""
                DataFactory 任务执行失败告警
                ==========================
                调度编码: %s
                调度ID:   %d
                环境:     %s
                执行ID:   %s
                错误信息: %s
                触发时间: %s
                ==========================
                请及时排查处理。
                """,
                job.getJobCode(), job.getId(), event.getEnvironment(),
                event.getExecutionId(), event.getErrorMessage(),
                event.getFireTime() != null ? event.getFireTime().format(DTF) : "-");
    }

    private String buildTimeoutBody(JobTimeoutEvent event, ScheduleJob job) {
        return String.format("""
                DataFactory 任务执行超时告警
                ==========================
                调度编码: %s
                调度ID:   %d
                环境:     %s
                执行ID:   %s
                超时阈值: %d 秒
                开始时间: %s
                ==========================
                请及时排查处理。
                """,
                job.getJobCode(), job.getId(), event.getEnvironment(),
                event.getExecutionId(), event.getTimeoutSeconds(),
                event.getStartTime() != null ? event.getStartTime().format(DTF) : "-");
    }
}
