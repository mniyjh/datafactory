package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ComponentCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.ComponentFieldSaveDTO;
import com.cqie.datafactory.configuration.controller.vo.ComponentMetaVO;
import com.cqie.datafactory.configuration.controller.vo.ComponentVO;

import java.util.List;
import java.util.Map;
import com.cqie.datafactory.configuration.service.ComponentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/component")
public class ComponentController {

    @Autowired
    private ComponentService componentService;

    @GetMapping
    public Result<PageResult<ComponentVO>> pageComponent(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(current);
        pageQuery.setSize(size);
        return Result.success(componentService.pageComponent(pageQuery, keyword));
    }

    @PostMapping
    public Result<Long> createComponent(@RequestBody ComponentCreateDTO dto) {
        return Result.success(componentService.createComponent(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> updateComponent(@PathVariable("id") Long id, @RequestBody ComponentCreateDTO dto) {
        componentService.updateComponent(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteComponent(@PathVariable("id") Long id) {
        componentService.deleteComponent(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable("id") Long id) {
        componentService.toggleStatus(id);
        return Result.success();
    }

    @GetMapping("/{id}/meta")
    public Result<ComponentMetaVO> getComponentMeta(@PathVariable("id") Long id) {
        return Result.success(componentService.getComponentMeta(id));
    }

    @PutMapping("/{id}/fields")
    public Result<Void> saveComponentFields(@PathVariable("id") Long id, @RequestBody List<ComponentFieldSaveDTO> fields) {
        componentService.saveComponentFields(id, fields);
        return Result.success();
    }

    @PostMapping("/{componentId}/syncToTask/{taskId}")
    public Result<Void> syncToTask(@PathVariable("componentId") Long componentId, @PathVariable("taskId") Long taskId) {
        componentService.syncToTask(componentId, taskId);
        return Result.success();
    }

    @GetMapping("/{id}/fields/versions")
    public Result<String> getComponentFieldVersions(@PathVariable("id") Long id) {
        return Result.success(componentService.getComponentFieldVersions(id));
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testComponent(@PathVariable("id") Long id) {
        return Result.success(componentService.testComponent(id));
    }

    @PostMapping("/{id}/sync-nodes")
    public Result<Integer> syncComponentNodes(@PathVariable("id") Long id) {
        return Result.success(componentService.syncComponentFieldsToNodes(id));
    }
}
