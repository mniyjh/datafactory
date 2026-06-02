package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.DatasourceDbVersionVO;
import com.cqie.datafactory.configuration.service.DatasourceDbVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/datasource/db-version")
public class DatasourceDbVersionController {

    @Autowired
    private DatasourceDbVersionService versionService;

    @GetMapping
    public Result<List<DatasourceDbVersionVO>> listVersions(@RequestParam("dbId") Long dbId,
                                                             @RequestParam(value = "environment", required = false) String environment) {
        return Result.success(versionService.listVersions(dbId, environment));
    }

    @PostMapping
    public Result<Void> createVersion(@RequestBody DatasourceDbVersionCreateDTO dto) {
        versionService.createVersion(dto);
        return Result.success();
    }

    @PostMapping("/{id}/select")
    public Result<Void> selectVersion(@PathVariable("id") Long id) {
        versionService.selectVersion(id);
        return Result.success();
    }

    @PostMapping("/test")
    public Result<Void> testConnection(@RequestBody com.cqie.datafactory.configuration.controller.dto.DatasourceDbTestDTO dto) {
        versionService.testConnection(dto);
        return Result.success();
    }

    @PostMapping("/{id}/promote")
    public Result<Void> promote(@PathVariable("id") Long id, @RequestParam("toEnvironment") String toEnvironment) {
        versionService.promote(id, toEnvironment);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteVersion(@PathVariable("id") Long id) {
        versionService.deleteVersion(id);
        return Result.success();
    }
}