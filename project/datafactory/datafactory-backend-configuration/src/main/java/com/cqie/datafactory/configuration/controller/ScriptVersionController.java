package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ScriptVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ScriptVersionVO;
import com.cqie.datafactory.configuration.service.ScriptVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/script/version")
public class ScriptVersionController {

    @Autowired
    private ScriptVersionService versionService;

    @GetMapping("/{scriptId}")
    public Result<List<ScriptVersionVO>> listVersions(@PathVariable("scriptId") Long scriptId, @RequestParam(value = "environment", required = false) String environment) {
        return Result.success(versionService.listVersions(scriptId, environment));
    }

    @PostMapping
    public Result<Void> createVersion(@RequestBody ScriptVersionCreateDTO dto) {
        versionService.createVersion(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateVersion(@PathVariable("id") Long id, @RequestBody ScriptVersionCreateDTO dto) {
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

    @PostMapping("/{id}/current")
    public Result<Void> selectCurrent(@PathVariable("id") Long id) {
        versionService.selectCurrent(id);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testScript(@PathVariable("id") Long id,
                                                   @RequestBody(required = false) String inputJson) {
        return Result.success(versionService.testScript(id, inputJson));
    }

    @PostMapping("/{id}/promote")
    public Result<Void> promote(@PathVariable("id") Long id,
                                @RequestParam("toEnvironment") String toEnvironment) {
        versionService.promote(id, toEnvironment);
        return Result.success();
    }
}
