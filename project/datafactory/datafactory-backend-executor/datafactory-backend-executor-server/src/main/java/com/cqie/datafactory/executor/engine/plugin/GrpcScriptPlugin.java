package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.grpc.GrpcPythonClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GrpcScriptPlugin implements ComponentPlugin {

    private final GrpcPythonClient grpcClient;
    private final ObjectMapper objectMapper;

    public GrpcScriptPlugin(GrpcPythonClient grpcClient, ObjectMapper objectMapper) {
        this.grpcClient = grpcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("GRPC_PYTHON"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();

        String scriptContent = readFieldValue(fieldValues, "scriptContent", "code");
        if (scriptContent.isBlank()) {
            throw new BusinessException("GRPC_PYTHON组件缺少脚本内容");
        }

        String scriptType = readFieldValue(fieldValues, "scriptType", "type");
        if (scriptType.isBlank()) scriptType = "PYTHON";

        int timeout = 30;
        String timeoutStr = readFieldValue(fieldValues, "timeout");
        if (!timeoutStr.isBlank()) {
            try { timeout = Integer.parseInt(timeoutStr); } catch (NumberFormatException ignored) {}
        }

        String workDir = readFieldValue(fieldValues, "workDir", "work_dir");

        Map<String, String> inputParams = new HashMap<>();
        context.getResolvedInputs().forEach((k, v) -> inputParams.put(k, Objects.toString(v, "")));

        Map<String, Object> grpcResp = grpcClient.execute(
                scriptContent, scriptType, inputParams, timeout, workDir);

        Map<String, Object> result = new HashMap<>();
        result.put("exitCode", grpcResp.getOrDefault("exit_code", 0));
        result.put("stdout", grpcResp.getOrDefault("stdout", ""));
        result.put("durationMs", grpcResp.getOrDefault("duration_ms", 0));

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

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) return val.asText();
        }
        return "";
    }
}
