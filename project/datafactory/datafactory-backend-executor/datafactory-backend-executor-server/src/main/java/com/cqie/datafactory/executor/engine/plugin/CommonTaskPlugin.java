package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.ExecEngine;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CommonTaskPlugin implements ComponentPlugin {

    private final ExecEngine execEngine;
    private final JdbcTemplate jdbcTemplate;

    public CommonTaskPlugin(@Lazy ExecEngine execEngine, JdbcTemplate jdbcTemplate) {
        this.execEngine = execEngine;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("COMMON_TASK", "TASK"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String subTaskId = readFieldValue(fieldValues, "subTaskId", "taskId");
        if (subTaskId.isBlank()) {
            throw new BusinessException("COMMON_TASK缺少subTaskId");
        }

        List<Map<String, Object>> taskRows = jdbcTemplate.queryForList(
                "SELECT id, task_name, status FROM task WHERE id=?", Long.parseLong(subTaskId));
        if (taskRows.isEmpty()) {
            throw new BusinessException("子任务不存在: " + subTaskId);
        }

        // 子任务始终使用 PROD 环境已发布的当前版本
        String env = "PROD";
        List<Map<String, Object>> dslRows = jdbcTemplate.queryForList(
                "SELECT dsl_content FROM task_dsl WHERE task_id=? AND environment=? AND is_current=1 AND publish_status=1 ORDER BY created_time DESC LIMIT 1",
                Long.parseLong(subTaskId), env);

        if (dslRows.isEmpty()) {
            throw new BusinessException("子任务未找到已发布版本: " + subTaskId);
        }

        String dslContent = Objects.toString(dslRows.get(0).get("dsl_content"), "");
        Map<String, Object> childParams = new HashMap<>(context.getResolvedInputs());

        return execEngine.execute(dslContent, env, childParams, null);
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) return val.asText();
        }
        return "";
    }
}
