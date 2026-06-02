package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.DatasourceFeignClient;
import com.cqie.datafactory.executor.feign.vo.DbVersionResolveVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DbPlugin implements ComponentPlugin {

    private final JdbcTemplate jdbcTemplate;
    private final DatasourceFeignClient datasourceFeignClient;

    public DbPlugin(JdbcTemplate jdbcTemplate, DatasourceFeignClient datasourceFeignClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceFeignClient = datasourceFeignClient;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("DB", "MYSQL", "DATABASE", "JDBC"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String sql = readFieldValue(fieldValues, "sql", "SQL", "query", "statement", "dbSql");
        if (sql == null || sql.isBlank()) {
            Object sqlFromInput = firstNonNull(
                    context.getResolvedInputs().get("sql"),
                    context.getResolvedInputs().get("query"));
            if (sqlFromInput != null) sql = Objects.toString(sqlFromInput, "");
        }
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("DB组件缺少sql配置");
        }

        String dbCode = readFieldValue(fieldValues, "datasource", "dbCode", "dataSource");

        JdbcTemplate execTemplate = jdbcTemplate;
        if (!dbCode.isBlank()) {
            execTemplate = buildDynamicTemplate(dbCode, context.getEnvironment());
        }

        List<Map<String, Object>> rows = execTemplate.queryForList(sql);
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("environment", context.getEnvironment());
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
