package com.cqie.datafactory.executor.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.entity.TaskTestConfig;
import com.cqie.datafactory.executor.service.TaskTestConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务测试配置管理")
@RestController
@RequestMapping("/task-test-config")
public class TaskTestConfigController {

    private final TaskTestConfigService taskTestConfigService;

    public TaskTestConfigController(TaskTestConfigService taskTestConfigService) {
        this.taskTestConfigService = taskTestConfigService;
    }

    @Operation(summary = "获取任务的测试配置（按版本隔离）")
    @GetMapping("/list/{taskId}")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<List<TaskTestConfig>> list(@PathVariable("taskId") Long taskId,
                                             @RequestParam(value = "versionId", required = false) Long versionId) {
        return Result.success(taskTestConfigService.listByTaskIdAndVersion(taskId, versionId));
    }

    @Operation(summary = "保存测试配置")
    @PostMapping
    @PreAuthorize("hasAuthority('task:write')")
    public Result<TaskTestConfig> save(@RequestBody TaskTestConfig config) {
        return Result.success(taskTestConfigService.saveConfig(config));
    }

    @Operation(summary = "删除测试配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> delete(@PathVariable("id") Long id) {
        taskTestConfigService.deleteConfig(id);
        return Result.success();
    }
}
