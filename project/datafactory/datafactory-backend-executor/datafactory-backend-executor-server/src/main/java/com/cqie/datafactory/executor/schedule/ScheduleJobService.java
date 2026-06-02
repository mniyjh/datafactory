package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduleJobService extends ServiceImpl<ScheduleJobMapper, ScheduleJob> {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobService.class);

    public List<ScheduleJob> listEnabledJobs() {
        return list(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getStatus, 1));
    }

    @Override
    public boolean save(ScheduleJob job) {
        computeNextFireTime(job);
        return super.save(job);
    }

    @Override
    public boolean updateById(ScheduleJob job) {
        computeNextFireTime(job);
        return super.updateById(job);
    }

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
}
