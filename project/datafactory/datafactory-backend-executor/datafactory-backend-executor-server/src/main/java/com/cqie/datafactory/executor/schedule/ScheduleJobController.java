package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobAuditLog;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobDailyStats;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobTask;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobAuditLogMapper;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobDailyStatsMapper;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/schedule")
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;
    private final TaskScheduleExecutor taskScheduleExecutor;
    private final ExecutionLogService executionLogService;
    private final ScheduleJobAuditLogMapper auditLogMapper;
    private final ScheduleJobDailyStatsMapper statsMapper;

    public ScheduleJobController(ScheduleJobService scheduleJobService,
                                  TaskScheduleExecutor taskScheduleExecutor,
                                  ExecutionLogService executionLogService,
                                  ScheduleJobAuditLogMapper auditLogMapper,
                                  ScheduleJobDailyStatsMapper statsMapper) {
        this.scheduleJobService = scheduleJobService;
        this.taskScheduleExecutor = taskScheduleExecutor;
        this.executionLogService = executionLogService;
        this.auditLogMapper = auditLogMapper;
        this.statsMapper = statsMapper;
    }

    // ===================== CRUD =====================

    @GetMapping
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<List<ScheduleJob>> list() {
        List<ScheduleJob> jobs = scheduleJobService.list();
        scheduleJobService.loadJobTasksBatch(jobs);
        return Result.success(jobs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<ScheduleJob> get(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        scheduleJobService.loadJobTasks(job);
        return Result.success(job);
    }

    /** 查询定时任务关联的所有任务 */
    @GetMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<List<ScheduleJobTask>> getJobTasks(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        scheduleJobService.loadJobTasks(job);
        return Result.success(job.getJobTasks());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule:write')")
    public Result<Long> create(@RequestBody ScheduleJob job) {
        scheduleJobService.save(job);
        return Result.success(job.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:write')")
    public Result<?> update(@PathVariable("id") Long id, @RequestBody ScheduleJob job) {
        job.setId(id);
        scheduleJobService.updateById(job);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:write')")
    public Result<?> delete(@PathVariable("id") Long id) {
        scheduleJobService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('schedule:write')")
    public Result<?> toggle(@PathVariable("id") Long id) {
        boolean ok = scheduleJobService.toggleStatus(id);
        return ok ? Result.success() : Result.fail("定时任务不存在");
    }

    @PostMapping("/{id}/trigger")
    @PreAuthorize("hasAuthority('schedule:write')")
    public Result<Map<String, Object>> trigger(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        try {
            taskScheduleExecutor.fireJob(job);
            Map<String, Object> result = new HashMap<>();
            result.put("executionId", job.getLastExecutionId());
            result.put("message", "触发成功");
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("触发失败: " + e.getMessage());
        }
    }

    // ===================== 执行历史 =====================

    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<List<ExecutionLog>> executions(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        List<ExecutionLog> logs = executionLogService.list(
                new LambdaQueryWrapper<ExecutionLog>()
                        .eq(ExecutionLog::getScheduleJobId, id)
                        .orderByDesc(ExecutionLog::getStartTime));
        return Result.success(logs);
    }

    // ===================== 审计日志 =====================

    @GetMapping("/{id}/audit-logs")
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<List<ScheduleJobAuditLog>> auditLogs(@PathVariable("id") Long id) {
        List<ScheduleJobAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<ScheduleJobAuditLog>()
                        .eq(ScheduleJobAuditLog::getJobId, id)
                        .orderByDesc(ScheduleJobAuditLog::getChangedTime));
        return Result.success(logs);
    }

    // ===================== 调度统计 =====================

    @GetMapping("/{id}/daily-stats")
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<List<ScheduleJobDailyStats>> dailyStats(
            @PathVariable("id") Long id,
            @RequestParam(value = "days", defaultValue = "7") int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        List<ScheduleJobDailyStats> stats = statsMapper.selectList(
                new LambdaQueryWrapper<ScheduleJobDailyStats>()
                        .eq(ScheduleJobDailyStats::getJobId, id)
                        .ge(ScheduleJobDailyStats::getStatDate, since)
                        .orderByDesc(ScheduleJobDailyStats::getStatDate));
        return Result.success(stats);
    }

    @GetMapping("/{id}/stats-summary")
    @PreAuthorize("hasAuthority('schedule:read')")
    public Result<Map<String, Object>> statsSummary(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");

        Map<String, Object> summary = new HashMap<>();

        // 从 execution_log 实时统计（最近30天）
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<ExecutionLog> logs = executionLogService.list(
                new LambdaQueryWrapper<ExecutionLog>()
                        .eq(ExecutionLog::getScheduleJobId, id)
                        .ge(ExecutionLog::getStartTime, thirtyDaysAgo));

        long totalExec = logs.size();
        long totalSuccess = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long totalFailure = logs.stream().filter(l -> "FAILURE".equals(l.getStatus())).count();
        long totalTimeout = logs.stream().filter(l -> "TIMEOUT".equals(l.getStatus())).count();
        long totalRunning = logs.stream().filter(l -> "RUNNING".equals(l.getStatus())).count();

        // 平均执行时长
        double avgDuration = logs.stream()
                .filter(l -> l.getDurationMs() != null && l.getDurationMs() > 0)
                .mapToLong(ExecutionLog::getDurationMs)
                .average().orElse(0);

        summary.put("jobId", id);
        summary.put("jobCode", job.getJobCode());
        summary.put("days", 30);
        summary.put("totalExecutions", totalExec);
        summary.put("totalSuccess", totalSuccess);
        summary.put("totalFailure", totalFailure);
        summary.put("totalTimeout", totalTimeout);
        summary.put("totalRunning", totalRunning);
        summary.put("avgDurationMs", Math.round(avgDuration));
        summary.put("successRate", totalExec > 0
                ? Math.round((double) totalSuccess / totalExec * 10000.0) / 100.0 : 0);
        summary.put("currentRetry", job.getCurrentRetry());

        return Result.success(summary);
    }
}
