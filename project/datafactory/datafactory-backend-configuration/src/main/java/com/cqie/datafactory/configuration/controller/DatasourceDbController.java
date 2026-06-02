package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbTestDTO;
import com.cqie.datafactory.configuration.controller.vo.DatasourceDbVO;
import com.cqie.datafactory.configuration.entity.DatasourceDb;
import com.cqie.datafactory.configuration.service.DatasourceDbService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/datasource/db")
public class DatasourceDbController {

    @Autowired
    private DatasourceDbService dbService;

    @PostMapping("/test")
    public Result<Void> testConnection(@RequestBody DatasourceDbTestDTO dto) {
        dbService.testConnection(dto);
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<DatasourceDbVO>> pageDb(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(current);
        pageQuery.setSize(size);
        return Result.success(dbService.pageDb(pageQuery, keyword));
    }

    @PostMapping
    public Result<Void> createDb(@RequestBody DatasourceDbCreateDTO dto) {
        dbService.createDb(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateDb(@PathVariable("id") Long id, @RequestBody DatasourceDbCreateDTO dto) {
        dbService.updateDb(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDb(@PathVariable("id") Long id) {
        dbService.deleteDb(id);
        return Result.success();
    }

    @GetMapping("/simple")
    public Result<List<Map<String, Object>>> simpleList() {
        List<DatasourceDb> list = dbService.list(
            new LambdaQueryWrapper<DatasourceDb>()
                .eq(DatasourceDb::getStatus, 1)
                .orderByAsc(DatasourceDb::getDbName)
        );
        List<Map<String, Object>> result = list.stream().map(db -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", db.getId());
            item.put("name", db.getDbName());
            item.put("code", db.getDbCode());
            return item;
        }).collect(Collectors.toList());
        return Result.success(result);
    }
}