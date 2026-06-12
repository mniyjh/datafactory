package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.OpenApiCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.OpenApiVO;
import com.cqie.datafactory.configuration.service.OpenApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/open-api")
public class OpenApiController {

    @Autowired
    private OpenApiService apiService;

    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    public Result<PageResult<OpenApiVO>> pageApi(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(current);
        pageQuery.setSize(size);
        return Result.success(apiService.pageApi(pageQuery, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> createApi(@RequestBody OpenApiCreateDTO dto) {
        apiService.createApi(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> updateApi(@PathVariable("id") Long id, @RequestBody OpenApiCreateDTO dto) {
        apiService.updateApi(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> deleteApi(@PathVariable("id") Long id) {
        apiService.deleteApi(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> toggleStatus(@PathVariable("id") Long id) {
        apiService.toggleStatus(id);
        return Result.success();
    }

    @PostMapping("/{id}/key")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> generateKey(@PathVariable("id") Long id) {
        apiService.generateKey(id);
        return Result.success();
    }

    @PostMapping("/invoke/{code}")
    @PreAuthorize("hasAuthority('task:execute')")
    public Result<Map<String, Object>> invoke(@PathVariable("code") String code,
                                              @RequestHeader(value = "X-App-Secret", required = false) String appSecret,
                                              @RequestParam(value = "sync", defaultValue = "false") boolean sync,
                                              @RequestBody(required = false) Map<String, Object> payload) {
        if (sync) {
            return Result.success(apiService.invokeSync(code, appSecret, payload, 60000));
        }
        return Result.success(apiService.invokeByCode(code, appSecret, payload));
    }

    @GetMapping("/result/{executionId}")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Map<String, Object>> queryResult(@PathVariable("executionId") String executionId) {
        return Result.success(apiService.queryResult(executionId));
    }
}
