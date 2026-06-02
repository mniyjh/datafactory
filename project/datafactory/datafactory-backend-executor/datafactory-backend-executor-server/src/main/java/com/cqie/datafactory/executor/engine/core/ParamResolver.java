package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParamResolver {

    private static final Logger log = LoggerFactory.getLogger(ParamResolver.class);

    public Object resolve(IoParamDef def, Map<String, Map<String, Object>> nodeOutputsMap) {
        String sourceType = def.getSourceType();
        JsonNode sourceValue = def.getSourceValue();

        log.info("ParamResolver.resolve: paramCode={}, sourceType={}, sourceValue={}, nodeOutputsMap keys={}",
                def.getParamCode(), sourceType, sourceValue, nodeOutputsMap.keySet());

        try {
            if ("CONST".equalsIgnoreCase(sourceType)) {
                if (sourceValue != null && !sourceValue.isNull()) {
                    Object result = sourceValue.isTextual() ? sourceValue.asText() : sourceValue.toString();
                    log.info("ParamResolver CONST result: {}", result);
                    return result;
                }
                return def.getDefaultValue();
            }

            if ("UPSTREAM_OUTPUT".equalsIgnoreCase(sourceType)) {
                Object result = resolveUpstreamValue(sourceValue, nodeOutputsMap);
                log.info("ParamResolver UPSTREAM_OUTPUT result: {}", result);
                return result;
            }

            if ("EXPRESSION".equalsIgnoreCase(sourceType)) {
                String expr = sourceValue != null ? sourceValue.asText("") : "";
                return "expr_result(" + expr + ")";
            }

            return def.getDefaultValue();
        } catch (Exception e) {
            throw new BusinessException("参数解析失败(paramCode=" + def.getParamCode()
                    + ", sourceType=" + sourceType + "): " + e.getMessage());
        }
    }

    private Object resolveUpstreamValue(JsonNode sourceValue, Map<String, Map<String, Object>> nodeOutputsMap) {
        if (sourceValue == null || sourceValue.isNull()) return null;

        String nodeId = null;
        String paramCode = null;

        if (sourceValue.isObject()) {
            nodeId = sourceValue.path("nodeId").asText(null);
            paramCode = sourceValue.path("paramCode").asText(null);
        } else if (sourceValue.isTextual()) {
            String text = sourceValue.asText();
            if (text.contains(".")) {
                String[] parts = text.split("\\.", 2);
                nodeId = parts[0];
                paramCode = parts.length > 1 ? parts[1] : null;
            }
        }

        if (nodeId == null || paramCode == null) return null;

        Map<String, Object> nodeOutput = nodeOutputsMap.get(nodeId);
        return nodeOutput != null ? nodeOutput.get(paramCode) : null;
    }
}
