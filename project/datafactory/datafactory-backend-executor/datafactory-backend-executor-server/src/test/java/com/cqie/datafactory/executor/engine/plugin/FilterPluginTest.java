package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FilterPluginTest {

    private FilterPlugin plugin;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        plugin = new FilterPlugin();
        mapper = new ObjectMapper();
    }

    @Test
    void shouldFilterRowsBySimpleEqCondition() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "SIMPLE");
        fieldValues.put("condition_field", "status");
        fieldValues.put("condition_op", "EQ");
        fieldValues.put("condition_value", "1");

        NodeDef node = new NodeDef();
        node.setId("filter1");
        node.setType("FILTER");
        node.setName("Test Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("id", "1", "status", "1", "name", "Alice"));
        rows.add(Map.of("id", "2", "status", "0", "name", "Bob"));
        rows.add(Map.of("id", "3", "status", "1", "name", "Charlie"));
        upstream.put("rows", rows);
        upstream.put("rowCount", 3);
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(2, result.get("rowCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(2, resultRows.size());
        assertEquals("Alice", resultRows.get(0).get("name"));
        assertEquals("Charlie", resultRows.get(1).get("name"));
    }

    @Test
    void shouldFilterRowsByExpressionMode() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "EXPRESSION");
        fieldValues.put("expression", "age > 25 && status == 'active'");

        NodeDef node = new NodeDef();
        node.setId("filter2");
        node.setType("FILTER");
        node.setName("Expr Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("name", "Alice", "age", "30", "status", "active")));
        rows.add(new LinkedHashMap<>(Map.of("name", "Bob", "age", "20", "status", "active")));
        rows.add(new LinkedHashMap<>(Map.of("name", "Charlie", "age", "35", "status", "inactive")));
        upstream.put("rows", rows);
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(1, result.get("rowCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(1, resultRows.size());
        assertEquals("Alice", resultRows.get(0).get("name"));
    }

    @Test
    void shouldSupportColumnMode() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "COLUMN");
        fieldValues.put("columns", "id,name");

        NodeDef node = new NodeDef();
        node.setId("filter3");
        node.setType("FILTER");
        node.setName("Col Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        upstream.put("rows", List.of(Map.of("id", "1", "name", "Alice", "status", "1")));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        Map<String, Object> row = resultRows.get(0);
        assertTrue(row.containsKey("id"));
        assertTrue(row.containsKey("name"));
        assertFalse(row.containsKey("status"));
    }

    @Test
    void shouldSupportContainsOperator() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "SIMPLE");
        fieldValues.put("condition_field", "name");
        fieldValues.put("condition_op", "CONTAINS");
        fieldValues.put("condition_value", "Al");

        NodeDef node = new NodeDef();
        node.setId("filter4");
        node.setType("FILTER");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        upstream.put("rows", List.of(Map.of("name", "Alice"), Map.of("name", "Bob"), Map.of("name", "Alex")));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(2, result.get("rowCount"));
    }

    @Test
    void shouldSupportNullCheckOperators() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "SIMPLE");
        fieldValues.put("condition_field", "optional");
        fieldValues.put("condition_op", "IS_NOT_NULL");

        NodeDef node = new NodeDef();
        node.setId("filter5");
        node.setType("FILTER");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        Map<String, Object> row1 = new HashMap<>(); row1.put("id", "1"); row1.put("optional", "yes");
        Map<String, Object> row2 = new HashMap<>(); row2.put("id", "2"); row2.put("optional", null);
        upstream.put("rows", List.of(row1, row2));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(1, result.get("rowCount"));
    }

    @Test
    void shouldPassthroughOnBlankFilterMode() {
        ObjectNode fieldValues = mapper.createObjectNode();
        NodeDef node = new NodeDef();
        node.setId("filter6");
        node.setType("FILTER");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        upstream.put("rows", List.of(Map.of("id", "1", "name", "Alice")));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(1, result.get("rowCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        assertEquals("Alice", resultRows.get(0).get("name"));
        assertTrue(resultRows.get(0).containsKey("id"));
    }
}
