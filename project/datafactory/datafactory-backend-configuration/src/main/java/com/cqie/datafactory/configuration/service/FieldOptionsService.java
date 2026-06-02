package com.cqie.datafactory.configuration.service;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.util.AesEncryptUtil;
import com.cqie.datafactory.configuration.controller.dto.FieldOptionsResolveDTO;
import com.cqie.datafactory.configuration.entity.ExternalApiVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class FieldOptionsService {

    private final ExternalApiVersionService externalApiVersionService;
    private final AesEncryptUtil aesEncryptUtil;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    public FieldOptionsService(ExternalApiVersionService externalApiVersionService,
                               AesEncryptUtil aesEncryptUtil,
                               ObjectMapper objectMapper,
                               JdbcTemplate jdbcTemplate) {
        this.externalApiVersionService = externalApiVersionService;
        this.aesEncryptUtil = aesEncryptUtil;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> resolve(FieldOptionsResolveDTO dto) {
        String sourceType = dto.getSourceType() != null ? dto.getSourceType().toUpperCase() : "";
        return switch (sourceType) {
            case "DB_QUERY" -> resolveDbQuery(dto);
            case "API_CALL" -> resolveApiCall(dto);
            case "SCRIPT" -> resolveScript(dto);
            case "TASK" -> resolveTask(dto);
            default -> throw new BusinessException("不支持的选项数据源类型: " + sourceType);
        };
    }

    private List<Map<String, Object>> resolveDbQuery(FieldOptionsResolveDTO dto) {
        if (dto.getDbId() == null && dto.getDbVersionId() == null) {
            return jdbcTemplate.queryForList(
                "SELECT db_code AS value, db_name AS label FROM datasource_db WHERE status=1 ORDER BY id");
        }
        if (dto.getQuery() == null || dto.getQuery().isBlank()) {
            Map<String, Object> version = resolveDbVersion(dto);
            // 无自定义查询时，默认返回该数据源的表列表
            return queryTableList(version);
        }

        Map<String, Object> version = resolveDbVersion(dto);
        return executeQuery(version, dto.getQuery());
    }

    private List<Map<String, Object>> queryTableList(Map<String, Object> version) {
        HikariDataSource ds = createDataSource(version);
        try (ds) {
            JdbcTemplate jt = new JdbcTemplate(ds);
            return jt.queryForList(
                "SELECT table_name AS value, table_name AS label FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() ORDER BY table_name");
        } catch (Exception e) {
            throw new BusinessException("查询表列表失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> executeQuery(Map<String, Object> version, String query) {
        HikariDataSource ds = createDataSource(version);
        try (ds) {
            JdbcTemplate jt = new JdbcTemplate(ds);
            return jt.queryForList(query);
        } catch (Exception e) {
            throw new BusinessException("动态选项查询失败: " + e.getMessage());
        }
    }

    private HikariDataSource createDataSource(Map<String, Object> version) {
        String jdbcUrl = Objects.toString(version.get("jdbc_url"), "");
        String username = Objects.toString(version.get("username"), "");
        String rawPassword = Objects.toString(version.get("password"), "");
        String password;
        try {
            password = aesEncryptUtil.decrypt(rawPassword);
        } catch (Exception e) {
            password = rawPassword;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(60000);
        return new HikariDataSource(config);
    }

    private Map<String, Object> resolveDbVersion(FieldOptionsResolveDTO dto) {
        if (dto.getDbVersionId() != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT jdbc_url, username, password FROM datasource_db_version WHERE id=?",
                    dto.getDbVersionId());
            if (rows.isEmpty()) throw new BusinessException("数据库版本不存在");
            return rows.get(0);
        }

        String env = (dto.getEnvironment() == null || dto.getEnvironment().isBlank())
                ? "PROD" : dto.getEnvironment().toUpperCase();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT jdbc_url, username, password FROM datasource_db_version " +
                        "WHERE db_id=? AND environment=? AND is_current=1 " +
                        "ORDER BY updated_time DESC LIMIT 1",
                dto.getDbId(), env);
        if (rows.isEmpty()) {
            throw new BusinessException("未找到可用的数据库版本");
        }
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveApiCall(FieldOptionsResolveDTO dto) {
        if (dto.getApiId() == null && dto.getApiVersionId() == null) {
            return jdbcTemplate.queryForList(
                "SELECT api_code AS value, api_name AS label FROM external_api WHERE status=1 ORDER BY id");
        }

        ExternalApiVersion version = resolveApiVersion(dto);
        String url = version.getRequestUrl();
        String method = version.getRequestMethod() != null ? version.getRequestMethod() : "GET";

        try {
            HttpHeaders headers = new HttpHeaders();
            if (version.getRequestHeaders() != null && !version.getRequestHeaders().isBlank()) {
                Map<String, Object> headerMap = objectMapper.readValue(
                        version.getRequestHeaders(), new TypeReference<>() {});
                headerMap.forEach((k, v) -> headers.set(k, Objects.toString(v, "")));
            }

            HttpEntity<?> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method.toUpperCase()), request, String.class);

            Object parsed = objectMapper.readValue(response.getBody(), Object.class);
            if (parsed instanceof List) {
                return (List<Map<String, Object>>) parsed;
            }
            if (parsed instanceof Map) {
                Map<String, Object> root = (Map<String, Object>) parsed;
                for (Object value : root.values()) {
                    if (value instanceof List) return (List<Map<String, Object>>) value;
                }
            }
            return List.of(Map.of("value", response.getBody()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("API选项查询失败: " + e.getMessage());
        }
    }

    private ExternalApiVersion resolveApiVersion(FieldOptionsResolveDTO dto) {
        if (dto.getApiVersionId() != null) {
            ExternalApiVersion v = externalApiVersionService.getById(dto.getApiVersionId());
            if (v == null) {
                throw new BusinessException("API版本不存在");
            }
            return v;
        }

        String env = (dto.getEnvironment() == null || dto.getEnvironment().isBlank())
                ? "PROD" : dto.getEnvironment().toUpperCase();
        ExternalApiVersion v = externalApiVersionService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExternalApiVersion>()
                        .eq(ExternalApiVersion::getApiId, dto.getApiId())
                        .eq(ExternalApiVersion::getEnvironment, env)
                        .eq(ExternalApiVersion::getIsCurrent, 1)
                        .eq(ExternalApiVersion::getPublishStatus, 1)
                        .orderByDesc(ExternalApiVersion::getUpdatedTime)
                        .last("limit 1"));
        if (v == null) {
            throw new BusinessException("未找到可用的API版本");
        }
        return v;
    }

    private List<Map<String, Object>> resolveScript(FieldOptionsResolveDTO dto) {
        if (dto.getScriptId() == null && dto.getScriptVersionId() == null) {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT s.script_code AS value, s.script_name AS label " +
                "FROM script s INNER JOIN script_version sv ON sv.script_id = s.id " +
                "WHERE s.status = 1 AND sv.environment = 'PROD' " +
                "AND sv.is_current = 1 AND sv.publish_status = 1 ORDER BY s.script_name");
        }
        // 解析脚本版本获取执行详情
        Map<String, Object> version = resolveScriptVersion(dto);
        String scriptType = Objects.toString(version.get("script_type"), "PYTHON").toUpperCase();
        String scriptContent = Objects.toString(version.get("script_code_content"), "");

        if (scriptContent.isBlank()) {
            throw new BusinessException("脚本内容为空");
        }

        try {
            if ("SQL".equals(scriptType)) {
                return jdbcTemplate.queryForList(scriptContent);
            }
            // Python/Shell 脚本：通过进程执行，stdout 返回 JSON 数组
            String fileExt = "PYTHON".equals(scriptType) ? ".py" : ".sh";
            String interpreter = "SHELL".equals(scriptType)
                    ? (System.getProperty("os.name").toLowerCase().contains("win") ? "cmd" : "bash")
                    : Objects.toString(version.get("interpreter_path"), "python");
            int timeout = version.get("timeout") instanceof Number n ? n.intValue() : 30;

            java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("df-opt-", fileExt);
            java.nio.file.Files.writeString(tempScript, scriptContent, java.nio.charset.StandardCharsets.UTF_8);

            ProcessBuilder pb;
            if ("SHELL".equals(scriptType) && System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder(interpreter, "/c", tempScript.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder(interpreter, tempScript.toAbsolutePath().toString());
            }
            pb.redirectErrorStream(false);
            Process process = pb.start();
            process.getOutputStream().close();

            boolean finished = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("脚本执行超时(" + timeout + "s)");
            }

            String stdout;
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                stdout = br.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }

            if (process.exitValue() != 0) {
                String stderr;
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    stderr = br.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
                }
                throw new BusinessException("脚本执行失败(exitCode=" + process.exitValue() + "): " + stderr);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parsed = objectMapper.readValue(stdout, List.class);
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("脚本选项加载失败: " + e.getMessage());
        }
    }

    private Map<String, Object> resolveScriptVersion(FieldOptionsResolveDTO dto) {
        if (dto.getScriptVersionId() != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT sv.script_code_content, s.script_type, sv.timeout, sv.interpreter_path " +
                    "FROM script_version sv JOIN script s ON s.id=sv.script_id WHERE sv.id=?",
                    dto.getScriptVersionId());
            if (rows.isEmpty()) throw new BusinessException("脚本版本不存在");
            return rows.get(0);
        }

        String env = (dto.getEnvironment() == null || dto.getEnvironment().isBlank())
                ? "PROD" : dto.getEnvironment().toUpperCase();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT sv.script_code_content, s.script_type, sv.timeout, sv.interpreter_path " +
                "FROM script_version sv JOIN script s ON s.id=sv.script_id " +
                "WHERE sv.script_id=? AND sv.environment=? AND sv.is_current=1 AND sv.publish_status=1 " +
                "ORDER BY sv.updated_time DESC LIMIT 1",
                dto.getScriptId(), env);
        if (rows.isEmpty()) {
            throw new BusinessException("未找到可用的脚本版本");
        }
        return rows.get(0);
    }

    /**
     * 加载已发布任务列表作为选项（仅 PROD 已发布版本）
     */
    private List<Map<String, Object>> resolveTask(FieldOptionsResolveDTO dto) {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT t.id AS value, t.task_name AS label, t.task_code AS code " +
            "FROM task t INNER JOIN task_dsl td ON td.task_id = t.id " +
            "WHERE t.status = 1 AND td.environment = 'PROD' " +
            "AND td.is_current = 1 AND td.publish_status = 1 ORDER BY t.task_name");
    }
}
