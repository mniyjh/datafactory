package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.ScriptFeignClient;
import com.cqie.datafactory.executor.feign.vo.ScriptExecutionVO;
import com.cqie.datafactory.executor.grpc.GrpcPythonClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ScriptPlugin implements ComponentPlugin {

    private static final Logger log = LoggerFactory.getLogger(ScriptPlugin.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;
    private final ScriptFeignClient scriptFeignClient;
    private final GrpcPythonClient grpcClient;

    public ScriptPlugin(JdbcTemplate jdbcTemplate, ScriptFeignClient scriptFeignClient,
                        GrpcPythonClient grpcClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.scriptFeignClient = scriptFeignClient;
        this.grpcClient = grpcClient;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("SCRIPT", "PYTHON", "SHELL"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String scriptCode = readFieldValue(fieldValues, "scriptCode", "script_code");
        if (scriptCode.isBlank()) {
            scriptCode = context.getNode().getComponentCode();
        }

        // 脚本始终使用 PROD 环境已发布的当前版本
        String env = "PROD";

        // Query the script table to get the script ID first
        List<Map<String, Object>> scriptRows = jdbcTemplate.queryForList(
                "SELECT id, script_code, script_type, status FROM script WHERE script_code=?",
                scriptCode);
        if (scriptRows.isEmpty()) {
            throw new BusinessException("脚本不存在: " + scriptCode);
        }

        String scriptType = Objects.toString(scriptRows.get(0).get("script_type"), "PYTHON").toUpperCase();
        Long scriptId = ((Number) scriptRows.get(0).get("id")).longValue();

        // Use Feign to fetch script version details from Configuration service
        Result<ScriptExecutionVO> feignResult = scriptFeignClient.resolveScriptVersion(scriptId, env);
        if (feignResult.getCode() != 0 || feignResult.getData() == null) {
            throw new BusinessException("脚本版本查询失败: " + feignResult.getMessage());
        }
        ScriptExecutionVO version = feignResult.getData();

        String scriptContent = version.getScriptCodeContent();
        int timeoutSec = version.getTimeout() != null ? version.getTimeout() : 30;
        String interpreterPath = version.getInterpreterPath() != null ? version.getInterpreterPath() : "python";
        String workDir = version.getWorkDir() != null ? version.getWorkDir() : "";
        String envVarsJson = version.getEnvVars();
        Map<String, String> scriptEnvVars = parseEnvVars(envVarsJson);
        // 注入 dependencies 供 Python gRPC Server 安装
        String dependenciesJson = version.getDependencies();
        if (dependenciesJson != null && !dependenciesJson.isBlank()) {
            scriptEnvVars = new HashMap<>(scriptEnvVars);
            scriptEnvVars.put("_DF_DEPENDENCIES", dependenciesJson);
        }

        try {
            if ("SQL".equals(scriptType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(scriptContent);
                Map<String, Object> result = new HashMap<>();
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put("exitCode", 0);
                return result;
            }

            // PYTHON 脚本通过 gRPC Python 执行服务执行（类方法调用模式）
            if ("PYTHON".equals(scriptType)) {
                String className = readFieldValue(fieldValues, "className");
                String methodName = readFieldValue(fieldValues, "methodName");

                Map<String, String> grpcParams = new HashMap<>();
                Map<String, Object> resolvedInputs = context.getResolvedInputs() == null
                        ? Collections.emptyMap() : context.getResolvedInputs();
                log.info("ScriptPlugin gRPC resolvedInputs: {}", resolvedInputs);
                for (Map.Entry<String, Object> entry : resolvedInputs.entrySet()) {
                    Object val = entry.getValue();
                    // 防御：如果值是未解析的 JsonNode/Map，跳过并记录警告
                    if (val instanceof com.fasterxml.jackson.databind.JsonNode jn && jn.isObject()) {
                        log.warn("ScriptPlugin gRPC: 参数 {} 的值仍是 JsonNode 对象(未解析): {}", entry.getKey(), jn);
                        grpcParams.put(entry.getKey(), "");
                    } else if (val instanceof Map) {
                        log.warn("ScriptPlugin gRPC: 参数 {} 的值仍是 Map 对象(未解析): {}", entry.getKey(), val);
                        grpcParams.put(entry.getKey(), "");
                    } else {
                        grpcParams.put(entry.getKey(), Objects.toString(val, ""));
                    }
                }
                Map<String, Object> resolvedVars = context.getResolvedVars();
                for (Map.Entry<String, Object> entry : resolvedVars.entrySet()) {
                    grpcParams.put(entry.getKey(), Objects.toString(entry.getValue(), ""));
                }

                Map<String, Object> grpcResp = grpcClient.execute(
                        scriptContent, scriptType, className, methodName,
                        grpcParams, timeoutSec, workDir, scriptEnvVars);

                Map<String, Object> result = new HashMap<>();
                result.put("exitCode", grpcResp.getOrDefault("exit_code", 0));
                result.put("stdout", grpcResp.getOrDefault("stdout", ""));
                result.put("stderr", grpcResp.getOrDefault("stderr", ""));
                result.put("scriptCode", scriptCode);
                Object resultJson = grpcResp.get("result_json");
                if (resultJson instanceof String s && !s.isBlank()) {
                    try {
                        result.put("result", objectMapper.readValue(s, Object.class));
                    } catch (Exception e) {
                        result.put("result", s);
                    }
                }
                return result;
            }

            // SHELL 脚本本地子进程执行
            boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
            boolean isShell = "SHELL".equals(scriptType);
            String fileExt = "PYTHON".equals(scriptType) ? ".py" : (isWin ? ".bat" : ".sh");
            String interpreter = isShell ? (isWin ? "cmd" : "bash") : interpreterPath;

            // Windows .bat 自动加 @echo off 避免输出回显
            String contentToWrite = (isShell && isWin && !scriptContent.startsWith("@echo off"))
                    ? "@echo off\r\n" + scriptContent : scriptContent;

            Path tempScript = Files.createTempFile("df-script-", fileExt);
            Files.writeString(tempScript, contentToWrite, StandardCharsets.UTF_8);

            ProcessBuilder pb;
            if (isShell && isWin) {
                pb = new ProcessBuilder(interpreter, "/c", tempScript.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder(interpreter, tempScript.toAbsolutePath().toString());
            }
            if (!workDir.isBlank()) pb.directory(Path.of(workDir).toFile());
            // 注入脚本版本配置的环境变量
            if (!scriptEnvVars.isEmpty()) pb.environment().putAll(scriptEnvVars);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            Map<String, Object> scriptInput = new HashMap<>(
                    context.getResolvedInputs() == null ? Collections.emptyMap() : context.getResolvedInputs());
            // 合并上游节点的 result_var 输出（如 dbEmployees, apiUsers）
            scriptInput.putAll(context.getResolvedVars());
            String inputJson = objectMapper.writeValueAsString(scriptInput);
            process.getOutputStream().write((inputJson + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            // 在 waitFor 之前消费 stdout/stderr，防止管道缓冲区满导致进程阻塞
            java.util.concurrent.CompletableFuture<String> stdoutFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                                process.getInputStream(), StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (!sb.isEmpty()) sb.append("\n");
                                sb.append(line);
                            }
                            return sb.toString();
                        } catch (Exception e) {
                            return "";
                        }
                    });
            java.util.concurrent.CompletableFuture<String> stderrFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                                process.getErrorStream(), StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (!sb.isEmpty()) sb.append("\n");
                                sb.append(line);
                            }
                            return sb.toString();
                        } catch (Exception e) {
                            return "";
                        }
                    });

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            String stdout = stdoutFuture.getNow("");
            String stderr = stderrFuture.getNow("");
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("Python脚本执行超时(" + timeoutSec + "s), stdout=" + stdout + ", stderr=" + stderr);
            }

            int exitCode = process.exitValue();
            Map<String, Object> result = new HashMap<>();
            result.put("exitCode", exitCode);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("scriptCode", scriptCode);
            try {
                result.put("result", objectMapper.readValue(stdout, Object.class));
            } catch (Exception e) {
                result.put("result", stdout);
            }

            if (exitCode != 0) {
                throw new BusinessException("Python脚本执行失败(exitCode=" + exitCode + "): " + stderr);
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Python脚本执行异常: " + e.getMessage());
        }
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) return val.asText();
        }
        return "";
    }

    private Map<String, String> parseEnvVars(String envVarsJson) {
        if (envVarsJson == null || envVarsJson.isBlank()) return Collections.emptyMap();
        try {
            Map<String, String> result = new HashMap<>();
            Map<?, ?> parsed = objectMapper.readValue(envVarsJson, Map.class);
            parsed.forEach((k, v) -> result.put(String.valueOf(k), v != null ? String.valueOf(v) : ""));
            return result;
        } catch (Exception e) {
            log.warn("脚本环境变量 JSON 解析失败: {}", envVarsJson);
            return Collections.emptyMap();
        }
    }
}
