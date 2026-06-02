package com.cqie.datafactory.executor.controller;

import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.service.TaskDslService;
import com.cqie.datafactory.executor.service.dto.TaskDslCreateDTO;
import com.cqie.datafactory.executor.service.dto.TaskDslPromoteDTO;
import com.cqie.datafactory.executor.service.vo.TaskDslVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/task-dsl")
public class TaskDslController {

    private final TaskDslService taskDslService;

    public TaskDslController(TaskDslService taskDslService) {
        this.taskDslService = taskDslService;
    }

    @PostMapping("/version")
    public Result<Long> createVersion(@Valid @RequestBody TaskDslCreateDTO dto) {
        return Result.success(taskDslService.createVersion(dto));
    }

    @PutMapping("/version/{versionId}")
    public Result<Void> updateVersion(@PathVariable("versionId") Long versionId, @RequestBody TaskDslCreateDTO dto) {
        taskDslService.updateVersion(versionId, dto);
        return Result.success();
    }

    @PostMapping("/version/{versionId}/publish")
    public Result<Void> publish(@PathVariable("versionId") Long versionId) {
        taskDslService.publish(versionId);
        return Result.success();
    }

    @DeleteMapping("/version/{versionId}")
    public Result<Void> delete(@PathVariable("versionId") Long versionId) {
        taskDslService.delete(versionId);
        return Result.success();
    }

    @PostMapping("/version/{versionId}/current")
    public Result<Void> setCurrent(@PathVariable("versionId") Long versionId) {
        taskDslService.setCurrent(versionId);
        return Result.success();
    }

    @PostMapping("/{taskId}/promote")
    public Result<Void> promote(@PathVariable("taskId") Long taskId, @Valid @RequestBody TaskDslPromoteDTO dto) {
        taskDslService.promote(taskId, dto);
        return Result.success();
    }

    @PostMapping("/{taskId}/rollback-env")
    public Result<Void> rollbackEnv(@PathVariable("taskId") Long taskId, @Valid @RequestBody TaskDslPromoteDTO dto) {
        taskDslService.promote(taskId, dto);
        return Result.success();
    }

    @GetMapping("/{taskId}/versions")
    public Result<List<TaskDslVO>> versions(@PathVariable("taskId") Long taskId, @RequestParam(value = "environment", required = false) String environment) {
        return Result.success(taskDslService.listByTaskAndEnv(taskId, environment));
    }

    @GetMapping("/{taskId}/current")
    public Result<TaskDslVO> current(@PathVariable("taskId") Long taskId, @RequestParam("environment") String environment) {
        return Result.success(taskDslService.current(taskId, environment));
    }

    @GetMapping("/{taskId}/outdatedNodes")
    public Result<String> outdatedNodes(@PathVariable("taskId") Long taskId, @RequestParam("environment") String environment) {
        return Result.success(taskDslService.outdatedNodes(taskId, environment));
    }

    @GetMapping("/{taskId}/page")
    public Result<PageResult<TaskDslVO>> page(@PathVariable("taskId") Long taskId,
                                              @RequestParam(value = "environment", required = false) String environment,
                                              @RequestParam(value = "current", defaultValue = "1") Long current,
                                              @RequestParam(value = "size", defaultValue = "10") Long size) {
        return Result.success(taskDslService.page(taskId, environment, current, size));
    }

    @PostMapping("/{taskDslId}/sync-nodes")
    public Result<Void> syncNodes(@PathVariable("taskDslId") Long taskDslId) {
        taskDslService.setCurrent(taskDslId);
        return Result.success();
    }
}
