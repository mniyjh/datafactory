package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ScriptCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ScriptVO;
import com.cqie.datafactory.configuration.service.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/script")
public class ScriptController {

    @Autowired
    private ScriptService scriptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/simple")
    @PreAuthorize("hasAuthority('script:read')")
    public Result<List<Map<String, Object>>> simpleList() {
        List<Map<String, Object>> result = jdbcTemplate.queryForList(
            "SELECT DISTINCT s.id, s.script_name AS name, s.script_code AS code, s.script_type AS type " +
            "FROM script s INNER JOIN script_version sv ON sv.script_id = s.id " +
            "WHERE s.status = 1 AND sv.environment = 'PROD' " +
            "AND sv.is_current = 1 AND sv.publish_status = 1 ORDER BY s.script_name"
        );
        return Result.success(result);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('script:read')")
    public Result<PageResult<ScriptVO>> pageScript(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(current);
        pageQuery.setSize(size);
        return Result.success(scriptService.pageScript(pageQuery, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('script:write')")
    public Result<Void> createScript(@RequestBody ScriptCreateDTO dto) {
        scriptService.createScript(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('script:write')")
    public Result<Void> updateScript(@PathVariable("id") Long id, @RequestBody ScriptCreateDTO dto) {
        scriptService.updateScript(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('script:write')")
    public Result<Void> deleteScript(@PathVariable("id") Long id) {
        scriptService.deleteScript(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('script:write')")
    public Result<Void> toggleStatus(@PathVariable("id") Long id) {
        scriptService.toggleStatus(id);
        return Result.success();
    }
}
