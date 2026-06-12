package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ExternalApiVO;
import com.cqie.datafactory.configuration.entity.ExternalApi;
import com.cqie.datafactory.configuration.service.ExternalApiService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/external-api")
public class ExternalApiController {

    @Autowired
    private ExternalApiService apiService;

    @GetMapping
    @PreAuthorize("hasAuthority('datasource:read')")
    public Result<PageResult<ExternalApiVO>> pageApi(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(current);
        pageQuery.setSize(size);
        return Result.success(apiService.pageApi(pageQuery, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('datasource:write')")
    public Result<Void> createApi(@RequestBody ExternalApiCreateDTO dto) {
        apiService.createApi(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('datasource:write')")
    public Result<Void> updateApi(@PathVariable("id") Long id, @RequestBody ExternalApiCreateDTO dto) {
        apiService.updateApi(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('datasource:write')")
    public Result<Void> deleteApi(@PathVariable("id") Long id) {
        apiService.deleteApi(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('datasource:write')")
    public Result<Void> toggleStatus(@PathVariable("id") Long id) {
        apiService.toggleStatus(id);
        return Result.success();
    }

    @GetMapping("/simple")
    @PreAuthorize("hasAuthority('datasource:read')")
    public Result<List<Map<String, Object>>> simpleList() {
        List<ExternalApi> list = apiService.list(
            new LambdaQueryWrapper<ExternalApi>()
                .eq(ExternalApi::getStatus, 1)
                .orderByAsc(ExternalApi::getApiName)
        );
        List<Map<String, Object>> result = list.stream().map(api -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", api.getId());
            item.put("name", api.getApiName());
            item.put("code", api.getApiCode());
            return item;
        }).collect(Collectors.toList());
        return Result.success(result);
    }
}
