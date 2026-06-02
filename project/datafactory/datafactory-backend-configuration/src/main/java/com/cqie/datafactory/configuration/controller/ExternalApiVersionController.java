package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiVersionPromoteDTO;
import com.cqie.datafactory.configuration.controller.vo.ExternalApiVersionVO;
import com.cqie.datafactory.configuration.service.ExternalApiVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/external-api/version")
public class ExternalApiVersionController {

    @Autowired
    private ExternalApiVersionService versionService;

    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(@RequestBody ExternalApiVersionCreateDTO dto) {
        return Result.success(versionService.testConnection(dto));
    }

    @GetMapping("/{apiId}")
    public Result<List<ExternalApiVersionVO>> listVersions(@PathVariable("apiId") Long apiId,
            @RequestParam(value = "environment", required = false) String environment) {
        return Result.success(versionService.listVersions(apiId, environment));
    }

    @PostMapping
    public Result<Void> createVersion(@RequestBody ExternalApiVersionCreateDTO dto) {
        versionService.createVersion(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateVersion(@PathVariable("id") Long id, @RequestBody ExternalApiVersionCreateDTO dto) {
        versionService.updateVersion(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteVersion(@PathVariable("id") Long id) {
        versionService.deleteVersion(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publishVersion(@PathVariable("id") Long id) {
        versionService.publishVersion(id);
        return Result.success();
    }

    @PostMapping("/promote")
    public Result<Void> promoteVersion(@RequestBody ExternalApiVersionPromoteDTO dto) {
        versionService.promoteVersion(dto);
        return Result.success();
    }

    @PostMapping("/{id}/current")
    public Result<Void> selectCurrent(@PathVariable("id") Long id) {
        versionService.selectCurrent(id);
        return Result.success();
    }
}
