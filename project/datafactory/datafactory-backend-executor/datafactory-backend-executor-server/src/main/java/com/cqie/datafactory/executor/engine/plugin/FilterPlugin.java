package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.aviator.AviatorEvaluator;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FilterPlugin implements ComponentPlugin {

    @Override
    public Set<String> supportedTypes() { return Set.of("FILTER"); }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String filterMode = readFieldValue(fieldValues, "filterMode", "mode");
        String sourceNodeId = readFieldValue(fieldValues, "sourceNodeId", "sourceNode");

        Map<String, Object> sourceData = resolveSourceData(context, sourceNodeId);
        Object rowsObj = sourceData.get("rows");
        if (!(rowsObj instanceof List)) {
            return sourceData;
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;

        if (filterMode.isBlank()) {
            Map<String, Object> result = new HashMap<>(sourceData);
            result.put("rowCount", rows.size());
            return result;
        }

        if ("COLUMN".equalsIgnoreCase(filterMode)) {
            return applyColumnFilter(fieldValues, sourceData, rows);
        }

        if ("EXPRESSION".equalsIgnoreCase(filterMode)) {
            return applyExpressionFilter(fieldValues, sourceData, rows);
        }

        return applySimpleFilter(fieldValues, sourceData, rows);
    }

    private Map<String, Object> resolveSourceData(PluginContext context, String sourceNodeId) {
        Map<String, Object> sourceData = null;
        if (!sourceNodeId.isBlank()) {
            sourceData = context.getUpstreamOutputs().get(sourceNodeId);
        }
        if (sourceData == null) {
            for (Map<String, Object> v : context.getUpstreamOutputs().values()) {
                if (v.containsKey("rows")) { sourceData = v; break; }
            }
        }
        if (sourceData == null) {
            throw new BusinessException("FILTER组件未找到上游数据源");
        }
        return sourceData;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyColumnFilter(JsonNode fieldValues, Map<String, Object> sourceData,
                                                   List<Map<String, Object>> rows) {
        String columnsStr = readFieldValue(fieldValues, "columnFilter", "columns");
        if (columnsStr.isBlank()) {
            Map<String, Object> result = new HashMap<>(sourceData);
            result.put("rowCount", rows.size());
            return result;
        }
        List<String> columns = Arrays.stream(columnsStr.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        List<Map<String, Object>> filtered = rows.stream()
                .map(row -> {
                    Map<String, Object> newRow = new LinkedHashMap<>();
                    for (String col : columns) {
                        newRow.put(col, row.getOrDefault(col, null));
                    }
                    return newRow;
                }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rows", filtered);
        result.put("rowCount", filtered.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applySimpleFilter(JsonNode fieldValues, Map<String, Object> sourceData,
                                                   List<Map<String, Object>> rows) {
        String condField = readFieldValue(fieldValues, "condition_field");
        String condOp = readFieldValue(fieldValues, "condition_op", "conditionOp").toUpperCase();
        String condValue = readFieldValue(fieldValues, "condition_value", "conditionValue");

        if (condField.isBlank() || condOp.isBlank()) {
            Map<String, Object> result = new HashMap<>(sourceData);
            result.put("rowCount", rows.size());
            return result;
        }

        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> matchCondition(row.get(condField), condOp, condValue))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rows", filtered);
        result.put("rowCount", filtered.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyExpressionFilter(JsonNode fieldValues, Map<String, Object> sourceData,
                                                       List<Map<String, Object>> rows) {
        String expression = readFieldValue(fieldValues, "expression", "filterExpression");
        if (expression.isBlank()) {
            throw new BusinessException("FILTER组件表达式模式缺少expression字段");
        }

        com.googlecode.aviator.Expression compiledExpr = AviatorEvaluator.compile(expression);

        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> {
                    try {
                        Map<String, Object> env = new HashMap<>();
                        for (Map.Entry<String, Object> col : row.entrySet()) {
                            Object val = col.getValue();
                            if (val instanceof String s) {
                                try { val = Long.parseLong(s); } catch (NumberFormatException e1) {
                                    try { val = Double.parseDouble(s); } catch (NumberFormatException e2) {}
                                }
                            }
                            env.put(col.getKey(), val);
                        }
                        Object evalResult = compiledExpr.execute(env);
                        return toBoolean(evalResult);
                    } catch (Exception e) {
                        throw new BusinessException("过滤表达式求值失败: " + expression + " — " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rows", filtered);
        result.put("rowCount", filtered.size());
        return result;
    }

    private boolean matchCondition(Object fieldValue, String op, String expectedStr) {
        switch (op) {
            case "IS_NULL":     return fieldValue == null;
            case "IS_NOT_NULL": return fieldValue != null;
            case "EQ":  return Objects.toString(fieldValue, "").equals(expectedStr);
            case "NEQ": return !Objects.toString(fieldValue, "").equals(expectedStr);
            case "CONTAINS": return Objects.toString(fieldValue, "").contains(expectedStr);
            case "IN": {
                Set<String> values = Arrays.stream(expectedStr.split(",")).map(String::trim).collect(Collectors.toSet());
                return values.contains(Objects.toString(fieldValue, ""));
            }
            case "GT": case "GTE": case "LT": case "LTE":
                return compareNumeric(fieldValue, op, expectedStr);
            default:
                throw new BusinessException("FILTER组件不支持的操作符: " + op);
        }
    }

    private boolean compareNumeric(Object fieldValue, String op, String expectedStr) {
        try {
            double fieldNum = Double.parseDouble(Objects.toString(fieldValue, "0"));
            double expectedNum = Double.parseDouble(expectedStr);
            return switch (op) {
                case "GT"  -> fieldNum > expectedNum;
                case "GTE" -> fieldNum >= expectedNum;
                case "LT"  -> fieldNum < expectedNum;
                case "LTE" -> fieldNum <= expectedNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
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
