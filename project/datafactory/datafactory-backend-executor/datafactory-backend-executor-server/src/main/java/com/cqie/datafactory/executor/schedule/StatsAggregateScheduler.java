package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobDailyStats;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobDailyStatsMapper;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每小时聚合调度执行统计，写入 schedule_job_daily_stats。
 */
@Component
public class StatsAggregateScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatsAggregateScheduler.class);

    private final ExecutionLogService executionLogService;
    private final ScheduleJobDailyStatsMapper statsMapper;

    public StatsAggregateScheduler(ExecutionLogService executionLogService,
                                    ScheduleJobDailyStatsMapper statsMapper) {
        this.executionLogService = executionLogService;
        this.statsMapper = statsMapper;
    }

    @Scheduled(cron = "0 5 * * * ?")
    public void aggregateHourly() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        LocalDate today = LocalDate.now();

        // 查询过去一小时内完成的执行记录（带 schedule_job_id）
        List<ExecutionLog> recentLogs = executionLogService.list(
                new LambdaQueryWrapper<ExecutionLog>()
                        .isNotNull(ExecutionLog::getScheduleJobId)
                        .ge(ExecutionLog::getStartTime, oneHourAgo)
                        .ne(ExecutionLog::getStatus, "RUNNING"));

        if (recentLogs.isEmpty()) return;

        // 按 jobId 分组
        Map<Long, List<ExecutionLog>> grouped = recentLogs.stream()
                .collect(Collectors.groupingBy(ExecutionLog::getScheduleJobId));

        for (Map.Entry<Long, List<ExecutionLog>> entry : grouped.entrySet()) {
            Long jobId = entry.getKey();
            List<ExecutionLog> logs = entry.getValue();

            ScheduleJobDailyStats stats = new ScheduleJobDailyStats();
            stats.setJobId(jobId);
            stats.setStatDate(today);
            stats.setTotalCount(logs.size());

            long success = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
            long failure = logs.stream().filter(l -> "FAILURE".equals(l.getStatus())).count();
            long timeout = logs.stream().filter(l -> "TIMEOUT".equals(l.getStatus())).count();

            stats.setSuccessCount((int) success);
            stats.setFailureCount((int) failure);
            stats.setTimeoutCount((int) timeout);

            // 时长统计
            List<Long> durations = logs.stream()
                    .map(ExecutionLog::getDurationMs)
                    .filter(d -> d != null && d > 0)
                    .toList();
            if (!durations.isEmpty()) {
                long sum = durations.stream().mapToLong(Long::longValue).sum();
                long max = durations.stream().mapToLong(Long::longValue).max().orElse(0);
                long min = durations.stream().mapToLong(Long::longValue).min().orElse(0);
                stats.setAvgDurationMs(sum / durations.size());
                stats.setMaxDurationMs(max);
                stats.setMinDurationMs(min);
            }

            // UPSERT: 每日累计更新
            try {
                ScheduleJobDailyStats existing = statsMapper.selectOne(
                        new LambdaQueryWrapper<ScheduleJobDailyStats>()
                                .eq(ScheduleJobDailyStats::getJobId, jobId)
                                .eq(ScheduleJobDailyStats::getStatDate, today));
                if (existing != null) {
                    existing.setTotalCount(existing.getTotalCount() + stats.getTotalCount());
                    existing.setSuccessCount(existing.getSuccessCount() + stats.getSuccessCount());
                    existing.setFailureCount(existing.getFailureCount() + stats.getFailureCount());
                    existing.setTimeoutCount(existing.getTimeoutCount() + stats.getTimeoutCount());
                    if (stats.getAvgDurationMs() > 0) {
                        long totalDuration = existing.getAvgDurationMs() * (existing.getTotalCount() - stats.getTotalCount())
                                + stats.getAvgDurationMs() * stats.getTotalCount();
                        existing.setAvgDurationMs(totalDuration / existing.getTotalCount());
                    }
                    if (stats.getMaxDurationMs() > existing.getMaxDurationMs()) {
                        existing.setMaxDurationMs(stats.getMaxDurationMs());
                    }
                    if (existing.getMinDurationMs() == 0 || (stats.getMinDurationMs() > 0 && stats.getMinDurationMs() < existing.getMinDurationMs())) {
                        existing.setMinDurationMs(stats.getMinDurationMs());
                    }
                    statsMapper.updateById(existing);
                } else {
                    statsMapper.insert(stats);
                }
            } catch (DuplicateKeyException e) {
                log.debug("统计记录已存在 (并发插入), jobId={}, date={}", jobId, today);
            } catch (Exception e) {
                log.error("聚合统计失败: jobId={}", jobId, e);
            }
        }
    }
}
