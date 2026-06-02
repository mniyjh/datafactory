package com.cqie.datafactory.executor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.entity.NodeExecutionLog;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/executor/log")
@RequiredArgsConstructor
public class ExecutionLogController {

    private final ExecutionLogService executionLogService;

    @GetMapping("/page")
    public Result<Page<ExecutionLog>> pageLogs(
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return Result.success(executionLogService.pageLogs(taskId, status, startTime, endTime, current, size));
    }

    @GetMapping("/detail/{executionId}")
    public Result<ExecutionLog> getDetail(@PathVariable("executionId") String executionId) {
        return Result.success(executionLogService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutionLog>()
                        .eq(ExecutionLog::getExecutionId, executionId)));
    }

    @GetMapping("/nodes/{executionId}")
    public Result<List<NodeExecutionLog>> listNodeLogs(@PathVariable("executionId") String executionId) {
        return Result.success(executionLogService.listNodeLogs(executionId));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(executionLogService.getStatistics());
    }

    @GetMapping("/stats/task/{taskId}")
    public Result<Map<String, Object>> getTaskStats(@PathVariable("taskId") Long taskId) {
        return Result.success(executionLogService.getTaskStatistics(taskId));
    }

    @GetMapping("/nodes/ranking/{executionId}")
    public Result<List<Map<String, Object>>> getNodeRanking(@PathVariable("executionId") String executionId) {
        return Result.success(executionLogService.getNodeTimeRanking(executionId));
    }
}
