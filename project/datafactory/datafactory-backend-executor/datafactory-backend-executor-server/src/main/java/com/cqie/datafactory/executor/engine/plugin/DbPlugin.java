package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.DatasourceFeignClient;
import com.cqie.datafactory.executor.feign.ScriptFeignClient;
import com.cqie.datafactory.executor.feign.vo.DbVersionResolveVO;
import com.cqie.datafactory.executor.feign.vo.ScriptExecutionVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DbPlugin implements ComponentPlugin {

    private final JdbcTemplate jdbcTemplate;
    private final DatasourceFeignClient datasourceFeignClient;
    private final ScriptFeignClient scriptFeignClient;

    public DbPlugin(JdbcTemplate jdbcTemplate, DatasourceFeignClient datasourceFeignClient,
                    ScriptFeignClient scriptFeignClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceFeignClient = datasourceFeignClient;
        this.scriptFeignClient = scriptFeignClient;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("DB", "MYSQL", "DATABASE", "JDBC"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();

        // 1. 获取 SQL：优先从脚本管理读取 PROD 版本
        String sql = resolveSql(context);
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("DB组件缺少SQL配置：请选择SQL脚本或填写内联SQL");
        }

        // 2. 解析数据源
        String dbCode = readFieldValue(fieldValues, "datasource", "dbCode", "dataSource");
        JdbcTemplate execTemplate = jdbcTemplate;
        if (!dbCode.isBlank()) {
            execTemplate = buildDynamicTemplate(dbCode, context.getEnvironment());
        }

        // 3. 替换 SQL 中的 ${param} / #{param} 占位符
        sql = resolveSqlPlaceholders(sql, context.getResolvedInputs());

        // 4. 执行
        List<Map<String, Object>> rows = execTemplate.queryForList(sql);
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("environment", context.getEnvironment());
        return result;
    }

    /**
     * 从脚本管理获取 SQL 内容（PROD 环境当前版本）。
     * 如果 scriptCode 为空则回退到内联 sql 字段（兼容旧任务）。
     */
    private String resolveSql(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String scriptCode = readFieldValue(fieldValues, "scriptCode");
        if (!scriptCode.isBlank()) {
            // 查 script 表获取脚本 ID 和类型
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, script_type FROM script WHERE script_code = ? AND status = 1", scriptCode);
            if (rows.isEmpty()) {
                throw new BusinessException("SQL脚本不存在或已禁用: " + scriptCode);
            }
            String scriptType = String.valueOf(rows.get(0).get("script_type")).toUpperCase();
            if (!"SQL".equals(scriptType)) {
                throw new BusinessException("脚本类型不匹配: 需要SQL类型, 实际为" + scriptType);
            }
            Long scriptId = ((Number) rows.get(0).get("id")).longValue();

            // Feign 拉取 PROD 版本内容
            Result<ScriptExecutionVO> result = scriptFeignClient.resolveScriptVersion(scriptId, "PROD");
            if (result.getCode() != 0 || result.getData() == null) {
                throw new BusinessException("SQL脚本版本查询失败: " + result.getMessage());
            }
            return result.getData().getScriptCodeContent();
        }

        // 回退：内联 SQL（兼容旧任务）
        String sql = readFieldValue(fieldValues, "sql", "SQL", "query", "statement", "dbSql");
        if (sql == null || sql.isBlank()) {
            Object sqlFromInput = firstNonNull(
                    context.getResolvedInputs() != null ? context.getResolvedInputs().get("sql") : null,
                    context.getResolvedInputs() != null ? context.getResolvedInputs().get("query") : null);
            if (sqlFromInput != null) sql = Objects.toString(sqlFromInput, "");
        }
        return sql;
    }

    /**
     * 替换 SQL 中的 ${param} 或 #{param} 占位符为上游传入的参数值。
     * 数字类型直接拼接，字符串类型加单引号（防注入：仅允许字母数字下划线中文）。
     */
    private String resolveSqlPlaceholders(String sql, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return sql;
        String result = sql;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (val == null) continue;
            String replacement;
            if (val instanceof Number) {
                replacement = val.toString();
            } else {
                String str = val.toString().replace("'", "''");
                replacement = "'" + str + "'";
            }
            result = result.replace("${" + key + "}", replacement);
            result = result.replace("#{" + key + "}", replacement);
        }
        return result;
    }

    private JdbcTemplate buildDynamicTemplate(String dbCode, String environment) {
        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList(
                "SELECT id FROM datasource_db WHERE db_code = ? AND status = 1", dbCode);
        if (dbRows.isEmpty()) {
            throw new BusinessException("数据源不存在: " + dbCode);
        }
        Long dbId = ((Number) dbRows.get(0).get("id")).longValue();

        Result<DbVersionResolveVO> result = datasourceFeignClient.resolveDbVersion(dbId, environment);
        if (result.getCode() != 0 || result.getData() == null) {
            throw new BusinessException("数据源版本解析失败: " + result.getMessage());
        }
        DbVersionResolveVO vo = result.getData();

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(vo.getJdbcUrl());
        ds.setUsername(vo.getUsername());
        ds.setPassword(vo.getPassword());
        return new JdbcTemplate(ds);
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null && !(v instanceof String s && s.isBlank())) return v;
        }
        return null;
    }
}
