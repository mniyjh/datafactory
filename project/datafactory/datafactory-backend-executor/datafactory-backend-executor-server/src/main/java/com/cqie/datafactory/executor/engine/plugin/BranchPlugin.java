package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
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
        JsonNode branches = fieldValues != null ? fieldValues.get("branches") : null;

        // Switch mode: branches array present and non-empty
        if (branches != null && branches.isArray() && branches.size() > 0) {
            return executeSwitch(branches, fieldValues, context);
        }

        // Original if/else mode (backward compatible)
        return executeIfElse(fieldValues, context);
    }

    private Map<String, Object> executeSwitch(JsonNode branches, JsonNode fieldValues,
                                               PluginContext context) {
        // Read separate targetNodeIds array (new format) for index-based mapping
        JsonNode targetNodeIdsNode = fieldValues != null ? fieldValues.get("targetNodeIds") : null;
        List<String> targetNodeIds = jsonArrayToList(targetNodeIdsNode);

        // Resolve edges as fallback (backward compat)
        List<EdgeDef> edges = resolveBranchEdges(context);
        Map<String, Object> env = null;
        int index = 0;
        for (JsonNode branch : branches) {
            // New format: branch is a plain string (expression only)
            // Old format: branch is an object {"expression":"...", "targetNodeId":"..."}
            String expression;
            String explicitTarget;
            if (branch.isTextual()) {
                expression = branch.asText();
                explicitTarget = "";
            } else {
                expression = branch.has("expression") ? branch.get("expression").asText() : "";
                explicitTarget = branch.has("targetNodeId") ? branch.get("targetNodeId").asText() : "";
            }

            if (expression.isBlank()) {
                index++;
                continue;
            }

            if (env == null) {
                env = buildEnv(String.valueOf(branches), context.getUpstreamOutputs(),
                        context.getResolvedInputs());
            }

            String normalizedExpr = stripVarWrappers(expression);
            try {
                Object evalResult = AviatorEvaluator.execute(normalizedExpr, env);
                if (toBoolean(evalResult)) {
                    // Resolve target: explicit in branch > targetNodeIds[index] > edge port > edge index
                    String targetNodeId = !explicitTarget.isBlank() ? explicitTarget
                            : (index < targetNodeIds.size() ? targetNodeIds.get(index) : "");
                    if (targetNodeId.isBlank()) {
                        targetNodeId = findTargetByPort(edges, String.valueOf(index),
                                index < edges.size() ? edges.get(index) : null);
                    }
                    Map<String, Object> outputs = new HashMap<>();
                    outputs.put("conditionResult", true);
                    outputs.put("nextNodeId", targetNodeId);
                    outputs.put("matchIndex", index);
                    return outputs;
                }
            } catch (Exception e) {
                throw new BusinessException(
                        "条件表达式求值失败: " + expression + " — " + e.getMessage());
            }
            index++;
        }

        // No branch matched — return empty nextNodeId
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("conditionResult", false);
        outputs.put("nextNodeId", "");
        outputs.put("matchIndex", -1);
        return outputs;
    }

    /** 将 JsonNode 数组转为 String 列表，非数组返回空列表 */
    private List<String> jsonArrayToList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private Map<String, Object> executeIfElse(JsonNode fieldValues, PluginContext context) {
        String expression = readFieldValue(fieldValues, "expression", "condition");
        if (expression.isBlank()) {
            throw new BusinessException("BRANCH组件缺少条件表达式");
        }

        // Resolve targets: explicit config > edge by sourcePort
        String trueTarget = readFieldValue(fieldValues, "trueBranch", "trueTargetNodeId");
        String falseTarget = readFieldValue(fieldValues, "falseBranch", "falseTargetNodeId");

        List<EdgeDef> edges = resolveBranchEdges(context);
        if (trueTarget.isBlank()) {
            trueTarget = findTargetByPort(edges, "true", edges.isEmpty() ? null : edges.get(0));
        }
        if (falseTarget.isBlank()) {
            falseTarget = findTargetByPort(edges, "false",
                    edges.size() > 1 ? edges.get(1) : null);
        }

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

    /** 获取 BRANCH 的出边，按 sourcePort 数值排序（非数字的放后面） */
    private List<EdgeDef> resolveBranchEdges(PluginContext context) {
        List<EdgeDef> edges = new ArrayList<>(context.getOutgoingEdges());
        edges.sort(Comparator.comparing(e -> {
            String port = e.getSourcePort() != null ? e.getSourcePort() : "";
            try { return String.format("%010d", Integer.parseInt(port)); } catch (NumberFormatException ex) {
                return "￿" + port; // non-numeric ports sort after numeric ones
            }
        }));
        return edges;
    }

    /** 从边列表中查找匹配 sourcePort 的 targetNodeId */
    private String findTargetByPort(List<EdgeDef> edges, String port, EdgeDef fallback) {
        for (EdgeDef e : edges) {
            String p = e.getSourcePort() != null ? e.getSourcePort() : "";
            if (p.equals(port) && e.getTargetNodeId() != null && !e.getTargetNodeId().isBlank()) {
                return e.getTargetNodeId();
            }
        }
        return fallback != null && fallback.getTargetNodeId() != null
                ? fallback.getTargetNodeId() : "";
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
