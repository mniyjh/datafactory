package com.cqie.datafactory.executor.statistics;

import com.cqie.datafactory.common.result.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('monitor:read')")
    public Result<Map<String, Object>> overview() {
        return Result.success(statisticsService.getOverview());
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('monitor:read')")
    public Result<?> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(statisticsService.getTrend(days));
    }

    @GetMapping("/task-rank")
    @PreAuthorize("hasAuthority('monitor:read')")
    public Result<?> taskRank() {
        return Result.success(statisticsService.getFailureTop10());
    }

    @GetMapping("/execution/{executionId}/progress")
    @PreAuthorize("hasAuthority('monitor:read')")
    public Result<?> executionProgress(@PathVariable String executionId) {
        return Result.success(statisticsService.getExecutionProgress(executionId));
    }
}
