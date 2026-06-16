package com.cqie.datafactory.executor.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/executor/metrics")
public class MetricsController {

    private final JdbcTemplate jdbcTemplate;

    public MetricsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('monitor:read')")
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();

        // JVM 内存
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMB", heap.getUsed() / 1024 / 1024);
        jvm.put("heapMaxMB", heap.getMax() / 1024 / 1024);
        jvm.put("heapUsagePercent", Math.round(100.0 * heap.getUsed() / heap.getMax()));
        Runtime rt = Runtime.getRuntime();
        jvm.put("processMemoryMB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        result.put("jvm", jvm);

        // 线程
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        result.put("threadCount", threads.getThreadCount());

        // 数据库连接池 (HikariCP)
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> hikariRows = jdbcTemplate.queryForList(
                "SHOW STATUS LIKE 'Threads_connected'");
            if (!hikariRows.isEmpty()) {
                db.put("mysqlActiveConnections", hikariRows.get(0).get("Value"));
            }
        } catch (Exception ignored) {}
        result.put("database", db);

        // 今日执行统计
        String today = LocalDate.now().toString();
        Map<String, Object> todayStats = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COUNT(*) AS total, " +
                "SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) AS success, " +
                "SUM(CASE WHEN status='FAILURE' THEN 1 ELSE 0 END) AS failure, " +
                "AVG(duration_ms) AS avgDurationMs " +
                "FROM execution_log WHERE DATE(start_time) = ?", today);
            if (!rows.isEmpty()) {
                Map<String, Object> s = rows.get(0);
                todayStats.put("total", s.get("total"));
                todayStats.put("success", s.get("success"));
                todayStats.put("failure", s.get("failure"));
                todayStats.put("avgDurationMs", s.get("avgDurationMs"));
            }
        } catch (Exception ignored) {}
        result.put("today", todayStats);

        // 最近执行
        try {
            List<Map<String, Object>> recent = jdbcTemplate.queryForList(
                "SELECT task_name, status, environment, start_time " +
                "FROM execution_log ORDER BY start_time DESC LIMIT 5");
            result.put("recentExecutions", recent);
        } catch (Exception ignored) {}

        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
