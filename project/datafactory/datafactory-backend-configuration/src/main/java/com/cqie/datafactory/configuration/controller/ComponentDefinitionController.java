package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ComponentDefinitionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ComponentDefinitionVO;
import com.cqie.datafactory.configuration.service.ComponentDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/component-definition")
public class ComponentDefinitionController {
    @Autowired
    private ComponentDefinitionService componentDefinitionService;

    @GetMapping
    public Result<PageResult<ComponentDefinitionVO>> page(@RequestParam(value = "current", defaultValue = "1") Long current,
                                                          @RequestParam(value = "size", defaultValue = "10") Long size,
                                                          @RequestParam(value = "keyword", required = false) String keyword) {
        PageQuery pq = new PageQuery();
        pq.setCurrent(current);
        pq.setSize(size);
        return Result.success(componentDefinitionService.pageDefinition(pq, keyword));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ComponentDefinitionCreateDTO dto) {
        componentDefinitionService.createDefinition(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ComponentDefinitionCreateDTO dto) {
        componentDefinitionService.updateDefinition(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        componentDefinitionService.deleteDefinition(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggle(@PathVariable Long id) {
        componentDefinitionService.toggleStatus(id);
        return Result.success();
    }
}
