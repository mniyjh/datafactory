package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/schedule")
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;
    private final ExecutorTaskService executorTaskService;
    private final ExecutionLogService executionLogService;

    public ScheduleJobController(ScheduleJobService scheduleJobService,
                                  ExecutorTaskService executorTaskService,
                                  ExecutionLogService executionLogService) {
        this.scheduleJobService = scheduleJobService;
        this.executorTaskService = executorTaskService;
        this.executionLogService = executionLogService;
    }

    @GetMapping
    public Result<List<ScheduleJob>> list() {
        return Result.success(scheduleJobService.list());
    }

    @GetMapping("/{id}")
    public Result<ScheduleJob> get(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        return Result.success(job);
    }

    @PostMapping
    public Result<Long> create(@RequestBody ScheduleJob job) {
        scheduleJobService.save(job);
        return Result.success(job.getId());
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable("id") Long id, @RequestBody ScheduleJob job) {
        job.setId(id);
        scheduleJobService.updateById(job);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable("id") Long id) {
        scheduleJobService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<?> toggle(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        job.setStatus(job.getStatus() != null && job.getStatus() == 1 ? 0 : 1);
        scheduleJobService.updateById(job);
        return Result.success();
    }

    @PostMapping("/{id}/trigger")
    public Result<String> trigger(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("versionId", job.getTaskVersionId());
            String executionId = executorTaskService.execute(
                    job.getTaskId(), params, job.getEnvironment(), "MANUAL_TRIGGER", job.getId());
            job.setLastExecutionId(executionId);
            job.setLastFireTime(LocalDateTime.now());
            scheduleJobService.computeNextFireTime(job);
            scheduleJobService.updateById(job);
            return Result.success(executionId);
        } catch (Exception e) {
            return Result.fail("触发失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/executions")
    public Result<List<ExecutionLog>> executions(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        List<ExecutionLog> logs = executionLogService.list(
                new LambdaQueryWrapper<ExecutionLog>()
                        .eq(ExecutionLog::getScheduleJobId, id)
                        .orderByDesc(ExecutionLog::getStartTime));
        return Result.success(logs);
    }
}
