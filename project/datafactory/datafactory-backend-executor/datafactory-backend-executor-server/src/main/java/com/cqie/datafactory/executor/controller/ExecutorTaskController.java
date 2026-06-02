package com.cqie.datafactory.executor.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskCreateDTO;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskUpdateDTO;
import com.cqie.datafactory.executor.controller.vo.ExecutorTaskVO;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import jakarta.validation.Valid;
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
    public Result<Long> create(@Valid @RequestBody ExecutorTaskCreateDTO dto) {
        return Result.success(executorTaskService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody ExecutorTaskUpdateDTO dto) {
        executorTaskService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        executorTaskService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object statusObj = body.get("status");
        String statusStr = statusObj != null ? String.valueOf(statusObj) : "";
        Integer status = "发布".equals(statusStr) || "1".equals(statusStr) || "启用".equals(statusStr)
                || "true".equals(statusStr) ? 1 : 0;
        executorTaskService.changeStatus(id, status);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ExecutorTaskVO> detail(@PathVariable("id") Long id) {
        return Result.success(executorTaskService.detail(id));
    }

    @GetMapping({"", "/page"})
    public Result<PageResult<ExecutorTaskVO>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(executorTaskService.page(current, size, keyword, status));
    }

    @PostMapping("/{id}/execute")
    public Result<String> execute(@PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(executorTaskService.execute(id, params, "PROD", "MANUAL", null));
    }

    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(executorTaskService.execute(id, params, "TEST", "MANUAL", null));
    }
}
