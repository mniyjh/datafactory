package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.aviator.AviatorEvaluator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParamResolver {

    private static final Logger log = LoggerFactory.getLogger(ParamResolver.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

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
                Object result = evaluateExpression(sourceValue, def, nodeOutputsMap);
                log.info("ParamResolver EXPRESSION result: {}", result);
                return result;
            }

            return def.getDefaultValue();
        } catch (Exception e) {
            throw new BusinessException("参数解析失败(paramCode=" + def.getParamCode()
                    + ", sourceType=" + sourceType + "): " + e.getMessage());
        }
    }

    /**
     * Evaluate an Aviator expression using upstream node outputs as the variable environment.
     * Supports ${var.field} notation which is resolved from nodeOutputsMap[nodeId][field].
     */
    private Object evaluateExpression(JsonNode sourceValue, IoParamDef def,
                                       Map<String, Map<String, Object>> nodeOutputsMap) {
        String expr = sourceValue != null ? sourceValue.asText("") : "";
        if (expr.isBlank()) return def.getDefaultValue();

        // Build flattened env from all upstream outputs
        Map<String, Object> env = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : nodeOutputsMap.entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> outputs = entry.getValue();
            if (outputs == null) continue;
            for (Map.Entry<String, Object> kv : outputs.entrySet()) {
                String paramCode = kv.getKey();
                Object val = kv.getValue();
                if (val instanceof String) {
                    // Try to parse JSON strings so Aviator can access nested fields
                    String strVal = (String) val;
                    if (strVal.trim().startsWith("{") && strVal.trim().endsWith("}")) {
                        try {
                            val = new com.fasterxml.jackson.databind.ObjectMapper().readValue(strVal, Map.class);
                        } catch (Exception ignore) {}
                    }
                }
                // Register both as nodeId.paramCode and paramCode alone
                env.put(nodeId + "." + paramCode, val);
                env.put(paramCode, val);
            }
        }

        // Strip ${...} wrappers and resolve dotted variables against nodeOutputsMap
        String resolvedExpr = expr;
        Matcher m = VAR_PATTERN.matcher(expr);
        while (m.find()) {
            String varName = m.group(1);
            if (varName.contains(".")) {
                String[] parts = varName.split("\\.", 2);
                Map<String, Object> nodeOut = nodeOutputsMap.get(parts[0]);
                if (nodeOut != null && nodeOut.containsKey(parts[1])) {
                    Object val = nodeOut.get(parts[1]);
                    env.put(varName, val);
                }
            }
        }
        resolvedExpr = m.replaceAll("$1");

        try {
            return AviatorEvaluator.execute(resolvedExpr, env);
        } catch (Exception e) {
            log.warn("Aviator evaluation failed for expression '{}', falling back to raw: {}", expr, e.getMessage());
            // If Aviator fails (e.g. plain text expression), return the raw value from env or the expression itself
            Object fromEnv = env.get(resolvedExpr);
            return fromEnv != null ? fromEnv : expr;
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
