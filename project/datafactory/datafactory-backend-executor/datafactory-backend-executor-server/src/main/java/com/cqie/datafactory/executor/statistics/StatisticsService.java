package com.cqie.datafactory.executor.statistics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class StatisticsService {

    private final JdbcTemplate jdbcTemplate;

    public StatisticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOverview() {
        String today = LocalDate.now().toString();
        Map<String, Object> overview = new LinkedHashMap<>();

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM execution_log WHERE DATE(start_time) = ?", Long.class, today);
        Long success = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM execution_log WHERE DATE(start_time) = ? AND status = 'SUCCESS'", Long.class, today);
        Long failure = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM execution_log WHERE DATE(start_time) = ? AND status = 'FAILURE'", Long.class, today);

        overview.put("total", total != null ? total : 0);
        overview.put("success", success != null ? success : 0);
        overview.put("failure", failure != null ? failure : 0);

        Double avgDuration = jdbcTemplate.queryForObject(
                "SELECT AVG(duration_ms) FROM execution_log WHERE DATE(start_time) = ?", Double.class, today);
        overview.put("avgDurationMs", avgDuration != null ? Math.round(avgDuration) : 0);

        return overview;
    }

    public List<Map<String, Object>> getTrend(int days) {
        return jdbcTemplate.queryForList(
                "SELECT DATE(start_time) as date, COUNT(1) as count, " +
                        "SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
                        "SUM(CASE WHEN status='FAILURE' THEN 1 ELSE 0 END) as failureCount " +
                        "FROM execution_log WHERE start_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                        "GROUP BY DATE(start_time) ORDER BY date", days);
    }

    public List<Map<String, Object>> getFailureTop10() {
        return jdbcTemplate.queryForList(
                "SELECT task_name, COUNT(1) as failureCount FROM execution_log " +
                        "WHERE status='FAILURE' AND start_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                        "GROUP BY task_name ORDER BY failureCount DESC LIMIT 10");
    }

    public Map<String, Object> getExecutionProgress(String executionId) {
        Map<String, Object> progress = new LinkedHashMap<>();
        Map<String, Object> execLog = jdbcTemplate.queryForMap(
                "SELECT execution_id, status FROM execution_log WHERE execution_id = ?", executionId);
        if (execLog == null) {
            progress.put("error", "执行记录不存在");
            return progress;
        }

        progress.put("executionId", executionId);
        progress.put("status", execLog.get("status"));

        List<Map<String, Object>> nodes = jdbcTemplate.queryForList(
                "SELECT node_id, node_name, node_type, status, duration_ms, error_message " +
                        "FROM node_execution_log WHERE execution_id = ? ORDER BY start_time", executionId);

        long completed = nodes.stream().filter(n -> "SUCCESS".equals(n.get("status"))).count();
        progress.put("currentNodeId", nodes.isEmpty() ? null : nodes.get(nodes.size() - 1).get("node_id"));
        progress.put("progress", nodes.isEmpty() ? 0 : (double) completed / nodes.size());
        progress.put("nodeStatuses", nodes);
        return progress;
    }
}
