package com.cqie.datafactory.executor.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskCreateDTO;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskUpdateDTO;
import com.cqie.datafactory.executor.controller.vo.ExecutorTaskVO;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class ExecutorTaskController {

    private final ExecutorTaskService executorTaskService;

    public ExecutorTaskController(ExecutorTaskService executorTaskService) {
        this.executorTaskService = executorTaskService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Long> create(@Valid @RequestBody ExecutorTaskCreateDTO dto) {
        return Result.success(executorTaskService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody ExecutorTaskUpdateDTO dto) {
        executorTaskService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> delete(@PathVariable("id") Long id) {
        executorTaskService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object statusObj = body.get("status");
        String statusStr = statusObj != null ? String.valueOf(statusObj) : "";
        Integer status = "发布".equals(statusStr) || "1".equals(statusStr) || "启用".equals(statusStr)
                || "true".equals(statusStr) ? 1 : 0;
        executorTaskService.changeStatus(id, status);
        return Result.success();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<ExecutorTaskVO> detail(@PathVariable("id") Long id) {
        return Result.success(executorTaskService.detail(id));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Long> count() {
        return Result.success(executorTaskService.count());
    }

    @GetMapping({"", "/page"})
    @PreAuthorize("hasAuthority('task:read')")
    public Result<PageResult<ExecutorTaskVO>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(executorTaskService.page(current, size, keyword, status));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('task:execute')")
    public Result<String> execute(@PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(executorTaskService.execute(id, params, "PROD", "MANUAL", null));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('task:execute')")
    public Result<String> test(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(executorTaskService.execute(id, params, "TEST", "MANUAL", null));
    }
}
