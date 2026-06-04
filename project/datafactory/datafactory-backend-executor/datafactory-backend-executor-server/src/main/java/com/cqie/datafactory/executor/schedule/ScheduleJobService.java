package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobAuditLog;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobAuditLogMapper;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduleJobService extends ServiceImpl<ScheduleJobMapper, ScheduleJob> {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobService.class);

    @Autowired
    private ScheduleJobAuditLogMapper auditLogMapper;

    @Lazy
    @Autowired(required = false)
    private HighFrequencyScheduler highFrequencyScheduler;

    @Autowired(required = false)
    private TaskExecutionQueue taskExecutionQueue;

    public List<ScheduleJob> listEnabledJobs() {
        return list(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getStatus, 1));
    }

    /**
     * 查询指定 task_id 对应的所有调度。
     */
    public List<ScheduleJob> listByTaskId(Long taskId) {
        return list(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getTaskId, taskId));
    }

    /**
     * 查询父调度的子调度列表。
     */
    public List<ScheduleJob> listChildJobs(Long parentJobId) {
        return list(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getParentJobId, parentJobId)
                .eq(ScheduleJob::getStatus, 1));
    }

    // ===================== CUD with audit =====================

    @Override
    public boolean save(ScheduleJob job) {
        computeNextFireTime(job);
        boolean result = super.save(job);
        if (result) {
            recordAudit(job.getId(), "CREATE", null, job);
            if (highFrequencyScheduler != null) {
                highFrequencyScheduler.onJobChanged(job);
            }
        }
        return result;
    }

    @Override
    public boolean updateById(ScheduleJob job) {
        ScheduleJob oldJob = getById(job.getId());
        if (oldJob == null) return false;

        computeNextFireTime(job);
        boolean result = super.updateById(job);
        if (result) {
            recordAudit(job.getId(), "UPDATE", oldJob, job);
            if (highFrequencyScheduler != null) {
                highFrequencyScheduler.onJobUpdated(oldJob, job);
            }
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        ScheduleJob oldJob = getById(id);
        if (oldJob == null) return false;

        boolean result = super.removeById(id);
        if (result) {
            recordAudit((Long) id, "DELETE", oldJob, null);
            if (highFrequencyScheduler != null) {
                highFrequencyScheduler.onJobRemoved(oldJob);
            }
            if (taskExecutionQueue != null) {
                taskExecutionQueue.clear((Long) id);
            }
        }
        return result;
    }

    /**
     * Toggle enable/disable with audit.
     */
    public boolean toggleStatus(Long id) {
        ScheduleJob job = getById(id);
        if (job == null) return false;
        int newStatus = (job.getStatus() != null && job.getStatus() == 1) ? 0 : 1;

        ScheduleJob oldSnapshot = copyForAudit(job);
        job.setStatus(newStatus);
        boolean result = super.updateById(job);
        if (result) {
            recordAudit(id, "TOGGLE", oldSnapshot, job);
            if (highFrequencyScheduler != null) {
                highFrequencyScheduler.onJobChanged(job);
            }
        }
        return result;
    }

    /**
     * Update only execution tracking fields without triggering scheduler hooks.
     */
    public void updateFireResult(ScheduleJob job) {
        super.updateById(job);
    }

    // ===================== Cron helpers =====================

    public void computeNextFireTime(ScheduleJob job) {
        String cronExpr = job.getCronExpression();
        if (cronExpr == null || cronExpr.isBlank()) {
            return;
        }
        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            LocalDateTime next = cron.next(LocalDateTime.now());
            if (next == null) {
                log.warn("Cron表达式未匹配到未来时间: jobId={}, cron={}", job.getId(), cronExpr);
            }
            job.setNextFireTime(next);
        } catch (IllegalArgumentException e) {
            log.warn("无效的Cron表达式: jobId={}, cron={}", job.getId(), cronExpr);
            job.setNextFireTime(null);
        }
    }

    // ===================== Audit =====================

    private void recordAudit(Long jobId, String changeType, ScheduleJob oldJob, ScheduleJob newJob) {
        if ("CREATE".equals(changeType)) {
            insertAuditLog(jobId, changeType, "ALL", null, "created");
        } else if ("DELETE".equals(changeType)) {
            insertAuditLog(jobId, changeType, "ALL", "exists", null);
        } else {
            compareAndLog(jobId, changeType, oldJob, newJob);
        }
    }

    private void compareAndLog(Long jobId, String changeType, ScheduleJob oldJob, ScheduleJob newJob) {
        List<ScheduleJobAuditLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        diff(logs, jobId, changeType, "cron_expression",
                oldJob.getCronExpression(), newJob.getCronExpression(), now);
        diff(logs, jobId, changeType, "status",
                String.valueOf(oldJob.getStatus()), String.valueOf(newJob.getStatus()), now);
        diff(logs, jobId, changeType, "task_version_id",
                String.valueOf(oldJob.getTaskVersionId()), String.valueOf(newJob.getTaskVersionId()), now);
        diff(logs, jobId, changeType, "environment",
                oldJob.getEnvironment(), newJob.getEnvironment(), now);
        diff(logs, jobId, changeType, "retry_count",
                String.valueOf(oldJob.getRetryCount()), String.valueOf(newJob.getRetryCount()), now);
        diff(logs, jobId, changeType, "retry_interval",
                String.valueOf(oldJob.getRetryInterval()), String.valueOf(newJob.getRetryInterval()), now);
        diff(logs, jobId, changeType, "executor_timeout",
                String.valueOf(oldJob.getExecutorTimeout()), String.valueOf(newJob.getExecutorTimeout()), now);
        diff(logs, jobId, changeType, "block_strategy",
                oldJob.getBlockStrategy(), newJob.getBlockStrategy(), now);
        diff(logs, jobId, changeType, "misfire_strategy",
                oldJob.getMisfireStrategy(), newJob.getMisfireStrategy(), now);
        diff(logs, jobId, changeType, "window_start",
                oldJob.getWindowStart() != null ? oldJob.getWindowStart().toString() : null,
                newJob.getWindowStart() != null ? newJob.getWindowStart().toString() : null, now);
        diff(logs, jobId, changeType, "window_end",
                oldJob.getWindowEnd() != null ? oldJob.getWindowEnd().toString() : null,
                newJob.getWindowEnd() != null ? newJob.getWindowEnd().toString() : null, now);
        diff(logs, jobId, changeType, "parent_job_id",
                String.valueOf(oldJob.getParentJobId()), String.valueOf(newJob.getParentJobId()), now);
        diff(logs, jobId, changeType, "alarm_email",
                oldJob.getAlarmEmail(), newJob.getAlarmEmail(), now);

        if (!logs.isEmpty()) {
            for (ScheduleJobAuditLog l : logs) {
                auditLogMapper.insert(l);
            }
        }
    }

    private void diff(List<ScheduleJobAuditLog> logs, Long jobId, String changeType,
                      String fieldName, String oldVal, String newVal, LocalDateTime now) {
        if (oldVal == null && newVal == null) return;
        if (oldVal != null && oldVal.equals(newVal)) return;

        ScheduleJobAuditLog audit = new ScheduleJobAuditLog();
        audit.setJobId(jobId);
        audit.setChangeType(changeType);
        audit.setFieldName(fieldName);
        audit.setOldValue(oldVal);
        audit.setNewValue(newVal);
        audit.setChangedTime(now);
        logs.add(audit);
    }

    private void insertAuditLog(Long jobId, String changeType, String field,
                                 String oldVal, String newVal) {
        ScheduleJobAuditLog audit = new ScheduleJobAuditLog();
        audit.setJobId(jobId);
        audit.setChangeType(changeType);
        audit.setFieldName(field);
        audit.setOldValue(oldVal);
        audit.setNewValue(newVal);
        audit.setChangedTime(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }

    private ScheduleJob copyForAudit(ScheduleJob source) {
        ScheduleJob copy = new ScheduleJob();
        copy.setJobCode(source.getJobCode());
        copy.setTaskId(source.getTaskId());
        copy.setTaskVersionId(source.getTaskVersionId());
        copy.setCronExpression(source.getCronExpression());
        copy.setEnvironment(source.getEnvironment());
        copy.setStatus(source.getStatus());
        copy.setRetryCount(source.getRetryCount());
        copy.setRetryInterval(source.getRetryInterval());
        copy.setCurrentRetry(source.getCurrentRetry());
        copy.setExecutorTimeout(source.getExecutorTimeout());
        copy.setBlockStrategy(source.getBlockStrategy());
        copy.setMaxQueueSize(source.getMaxQueueSize());
        copy.setMisfireStrategy(source.getMisfireStrategy());
        copy.setWindowStart(source.getWindowStart());
        copy.setWindowEnd(source.getWindowEnd());
        copy.setParentJobId(source.getParentJobId());
        copy.setAlarmOnFailure(source.getAlarmOnFailure());
        copy.setAlarmOnTimeout(source.getAlarmOnTimeout());
        copy.setAlarmEmail(source.getAlarmEmail());
        copy.setLastExecutionId(source.getLastExecutionId());
        copy.setLastFireTime(source.getLastFireTime());
        copy.setNextFireTime(source.getNextFireTime());
        return copy;
    }
}
