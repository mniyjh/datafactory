package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.aviator.AviatorEvaluator;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BranchPlugin implements ComponentPlugin {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    @Override
    public Set<String> supportedTypes() { return Set.of("BRANCH"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String expression = readFieldValue(fieldValues, "expression", "condition");
        if (expression.isBlank()) {
            throw new BusinessException("BRANCH组件缺少条件表达式");
        }

        String trueTarget = readFieldValue(fieldValues, "trueBranch", "trueTargetNodeId");
        String falseTarget = readFieldValue(fieldValues, "falseBranch", "falseTargetNodeId");

        Map<String, Object> env = buildEnv(expression, context.getUpstreamOutputs(), context.getResolvedInputs());
        String normalizedExpr = stripVarWrappers(expression);

        boolean result;
        try {
            Object evalResult = AviatorEvaluator.execute(normalizedExpr, env);
            result = toBoolean(evalResult);
        } catch (Exception e) {
            throw new BusinessException("条件表达式求值失败: " + expression + " — " + e.getMessage());
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("conditionResult", result);
        outputs.put("nextNodeId", result ? trueTarget : falseTarget);
        return outputs;
    }

    private Map<String, Object> buildEnv(String expr, Map<String, Map<String, Object>> upstreamOutputs,
                                         Map<String, Object> resolvedInputs) {
        Map<String, Object> env = new HashMap<>(resolvedInputs);
        // Resolve ${var} patterns in expression
        Matcher matcher = VAR_PATTERN.matcher(expr);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!env.containsKey(varName)) {
                Object value = resolveVariable(varName, upstreamOutputs, resolvedInputs);
                if (value != null) {
                    env.put(varName.replace(".", "_"), value);
                }
            }
        }
        // Also expose all upstream top-level keys as bare variables
        for (Map<String, Object> nodeOutput : upstreamOutputs.values()) {
            for (Map.Entry<String, Object> e : nodeOutput.entrySet()) {
                if (!env.containsKey(e.getKey())) {
                    env.put(e.getKey(), e.getValue());
                }
            }
            // Expose rowCount and rows from DB results
            if (nodeOutput.containsKey("rows")) {
                Object rowsObj = nodeOutput.get("rows");
                if (rowsObj instanceof List && !((List<?>) rowsObj).isEmpty()) {
                    Object firstRow = ((List<?>) rowsObj).get(0);
                    if (firstRow instanceof Map) {
                        for (Map.Entry<String, Object> col : ((Map<String, Object>) firstRow).entrySet()) {
                            if (!env.containsKey(col.getKey())) {
                                // Convert numeric strings to numbers for Aviator
                                Object val = col.getValue();
                                if (val instanceof String s) {
                                    try { val = Long.parseLong(s); } catch (NumberFormatException e2) {
                                        try { val = Double.parseDouble(s); } catch (NumberFormatException e3) {}
                                    }
                                }
                                env.put(col.getKey(), val);
                            }
                        }
                    }
                }
            }
        }
        return env;
    }

    private String stripVarWrappers(String expr) {
        Matcher matcher = VAR_PATTERN.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            matcher.appendReplacement(sb, varName.replace(".", "_"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolveVariable(String varName, Map<String, Map<String, Object>> upstreamOutputs,
                                   Map<String, Object> resolvedInputs) {
        if (resolvedInputs.containsKey(varName)) return resolvedInputs.get(varName);
        if (varName.contains(".")) {
            String[] parts = varName.split("\\.", 2);
            Map<String, Object> nodeOutput = upstreamOutputs.get(parts[0]);
            if (nodeOutput != null && nodeOutput.containsKey(parts[1])) {
                return nodeOutput.get(parts[1]);
            }
        }
        for (Map<String, Object> nodeOutput : upstreamOutputs.values()) {
            if (nodeOutput.containsKey(varName)) return nodeOutput.get(varName);
            // Search inside 'rows' arrays (DB query results)
            Object rows = nodeOutput.get("rows");
            if (rows instanceof List && !((List<?>) rows).isEmpty()) {
                Object firstRow = ((List<?>) rows).get(0);
                if (firstRow instanceof Map && ((Map<String, Object>) firstRow).containsKey(varName)) {
                    return ((Map<String, Object>) firstRow).get(varName);
                }
            }
            // Search inside any nested map values
            for (Object val : nodeOutput.values()) {
                if (val instanceof Map && ((Map<String, Object>) val).containsKey(varName)) {
                    return ((Map<String, Object>) val).get(varName);
                }
            }
        }
        return null;
    }

    private boolean toBoolean(Object result) {
        if (result instanceof Boolean b) return b;
        if (result instanceof Number n) return n.doubleValue() != 0;
        if (result == null) return false;
        return true;
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
