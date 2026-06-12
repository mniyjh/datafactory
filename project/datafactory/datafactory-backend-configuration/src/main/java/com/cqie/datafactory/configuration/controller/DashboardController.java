package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.vo.DashboardVO;
import com.cqie.datafactory.configuration.service.DatasourceDbService;
import com.cqie.datafactory.configuration.service.ExternalApiService;
import com.cqie.datafactory.configuration.service.ScriptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DatasourceDbService dbService;

    @Autowired
    private ExternalApiService externalApiService;

    @Autowired
    private ScriptService scriptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('monitor:read')")
    public Result<DashboardVO> getStats() {
        DashboardVO vo = new DashboardVO();
        vo.setDbCount(dbService.count());
        vo.setApiCount(externalApiService.count());
        vo.setScriptCount(scriptService.count());
        vo.setTaskCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task", Long.class));
        return Result.success(vo);
    }
}
