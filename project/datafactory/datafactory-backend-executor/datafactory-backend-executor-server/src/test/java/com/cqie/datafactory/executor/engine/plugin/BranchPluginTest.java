package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BranchPluginTest {

    private BranchPlugin plugin;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        plugin = new BranchPlugin();
        mapper = new ObjectMapper();
    }

    @Test
    void shouldEvaluateSimpleExpressionToTrue() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("expression", "${count} > 5");
        fieldValues.put("trueTargetNodeId", "nodeA");
        fieldValues.put("falseTargetNodeId", "nodeB");

        NodeDef node = new NodeDef();
        node.setId("branch1");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 10);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeA", result.get("nextNodeId"));
    }

    @Test
    void shouldEvaluateSimpleExpressionToFalse() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("expression", "${count} > 5");
        fieldValues.put("trueTargetNodeId", "nodeA");
        fieldValues.put("falseTargetNodeId", "nodeB");

        NodeDef node = new NodeDef();
        node.setId("branch2");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 3);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(false, result.get("conditionResult"));
        assertEquals("nodeB", result.get("nextNodeId"));
    }

    @Test
    void shouldThrowExceptionOnMissingExpression() {
        ObjectNode fieldValues = mapper.createObjectNode();
        NodeDef node = new NodeDef();
        node.setId("branch3");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertThrows(BusinessException.class, () -> plugin.execute(ctx));
    }

    // ==================== Switch mode tests ====================

    @Test
    void shouldUseSwitchModeAndHitFirstBranch() {
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b1 = mapper.createObjectNode();
        b1.put("expression", "${count} > 5");
        b1.put("targetNodeId", "nodeA");
        branches.add(b1);
        var b2 = mapper.createObjectNode();
        b2.put("expression", "${count} > 2");
        b2.put("targetNodeId", "nodeB");
        branches.add(b2);
        fieldValues.set("branches", branches);
        fieldValues.put("defaultTargetNodeId", "nodeDefault");

        NodeDef node = new NodeDef();
        node.setId("branchSw1");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 10);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeA", result.get("nextNodeId"));
        assertEquals(0, result.get("matchIndex"));
    }

    @Test
    void shouldUseSwitchModeAndHitMiddleBranch() {
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b1 = mapper.createObjectNode();
        b1.put("expression", "${count} > 50");
        b1.put("targetNodeId", "nodeHigh");
        branches.add(b1);
        var b2 = mapper.createObjectNode();
        b2.put("expression", "${count} > 5");
        b2.put("targetNodeId", "nodeMid");
        branches.add(b2);
        var b3 = mapper.createObjectNode();
        b3.put("expression", "${count} > 0");
        b3.put("targetNodeId", "nodeLow");
        branches.add(b3);
        fieldValues.set("branches", branches);
        fieldValues.put("defaultTargetNodeId", "nodeDefault");

        NodeDef node = new NodeDef();
        node.setId("branchSw2");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 10); // 10 > 50 false, 10 > 5 true → hits second branch

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeMid", result.get("nextNodeId"));
        assertEquals(1, result.get("matchIndex"));
    }

    @Test
    void shouldUseSwitchModeAndFallbackToEmpty() {
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b1 = mapper.createObjectNode();
        b1.put("expression", "${count} > 50");
        b1.put("targetNodeId", "nodeHigh");
        branches.add(b1);
        var b2 = mapper.createObjectNode();
        b2.put("expression", "${count} > 30");
        b2.put("targetNodeId", "nodeMid");
        branches.add(b2);
        fieldValues.set("branches", branches);
        // no defaultTargetNodeId — unmatched branches return empty nextNodeId

        NodeDef node = new NodeDef();
        node.setId("branchSw3");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 5); // no branch matches

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(false, result.get("conditionResult"));
        assertEquals("", result.get("nextNodeId"));
        assertEquals(-1, result.get("matchIndex"));
    }

    @Test
    void shouldUseSwitchModeWithNoDefault() {
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b1 = mapper.createObjectNode();
        b1.put("expression", "${count} > 50");
        b1.put("targetNodeId", "nodeHigh");
        branches.add(b1);
        fieldValues.set("branches", branches);
        // no defaultTargetNodeId set

        NodeDef node = new NodeDef();
        node.setId("branchSw4");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 5);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(false, result.get("conditionResult"));
        assertEquals("", result.get("nextNodeId"));
        assertEquals(-1, result.get("matchIndex"));
    }

    @Test
    void shouldUseSwitchModeWithMultiOperatorExpression() {
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b1 = mapper.createObjectNode();
        b1.put("expression", "${amount} > 1000 && ${status} == 'active'");
        b1.put("targetNodeId", "nodeVIP");
        branches.add(b1);
        var b2 = mapper.createObjectNode();
        b2.put("expression", "${amount} > 500 || ${level} >= 3");
        b2.put("targetNodeId", "nodeRegular");
        branches.add(b2);
        fieldValues.set("branches", branches);
        fieldValues.put("defaultTargetNodeId", "nodeBasic");

        NodeDef node = new NodeDef();
        node.setId("branchSw5");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("amount", 1200);
        resolvedInputs.put("status", "active");
        resolvedInputs.put("level", 1);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeVIP", result.get("nextNodeId"));
        assertEquals(0, result.get("matchIndex"));
    }

    @Test
    void shouldFallbackToIfElseModeWhenNoBranchesArray() {
        // Backward compatibility: old config with expression/trueTarget/falseTarget still works
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("expression", "${count} > 5");
        fieldValues.put("trueTargetNodeId", "nodeTrue");
        fieldValues.put("falseTargetNodeId", "nodeFalse");

        NodeDef node = new NodeDef();
        node.setId("branchBC");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 10);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeTrue", result.get("nextNodeId"));
        // if/else mode does not set matchIndex
        assertNull(result.get("matchIndex"));
    }

    // ==================== Edge-based dynamic routing tests ====================

    @Test
    void shouldResolveTargetFromEdgesBySourcePort() {
        // branches only have expression — targetNodeId comes from Edge sourcePort
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b0 = mapper.createObjectNode(); b0.put("expression", "${score} >= 90"); branches.add(b0);
        var b1 = mapper.createObjectNode(); b1.put("expression", "${score} >= 60"); branches.add(b1);
        var b2 = mapper.createObjectNode(); b2.put("expression", "${score} >= 30"); branches.add(b2);
        fieldValues.set("branches", branches);

        NodeDef node = new NodeDef();
        node.setId("branchEdge");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        // Edges with sourcePort matching branch index
        List<EdgeDef> edges = List.of(
                edge("e0", "branchEdge", "0", "nodeA"),
                edge("e1", "branchEdge", "1", "nodeB"),
                edge("e2", "branchEdge", "2", "nodeC"),
                edge("ed", "branchEdge", "default", "nodeFallback"));

        Map<String, Object> resolvedInputs = Map.of("score", 75);

        // score=75 → first branch (>=90) false, second (>=60) true → should hit nodeB
        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, Map.of(), Map.of(), edges);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeB", result.get("nextNodeId"));  // from edge with sourcePort "1"
        assertEquals(1, result.get("matchIndex"));
    }

    @Test
    void shouldReturnEmptyWhenNoBranchMatches() {
        // New behavior: no defaultTargetNodeId → unmatched returns empty nextNodeId
        ObjectNode fieldValues = mapper.createObjectNode();
        var branches = mapper.createArrayNode();
        var b0 = mapper.createObjectNode(); b0.put("expression", "${score} >= 90"); branches.add(b0);
        fieldValues.set("branches", branches);

        NodeDef node = new NodeDef();
        node.setId("branchDef");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        List<EdgeDef> edges = List.of(
                edge("e0", "branchDef", "0", "nodeHigh"),
                edge("ed", "branchDef", "default", "nodeFallback"));

        Map<String, Object> resolvedInputs = Map.of("score", 50);
        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, Map.of(), Map.of(), edges);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(false, result.get("conditionResult"));
        assertEquals("", result.get("nextNodeId"));
        assertEquals(-1, result.get("matchIndex"));
    }

    @Test
    void shouldResolveIfElseTargetsFromEdges() {
        // if/else mode without explicit targetNodeId — reads from edges by port "true"/"false"
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("expression", "${count} > 5");

        NodeDef node = new NodeDef();
        node.setId("branchIE");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        List<EdgeDef> edges = List.of(
                edge("et", "branchIE", "true", "nodeTruePath"),
                edge("ef", "branchIE", "false", "nodeFalsePath"));

        PluginContext ctx = new PluginContext(node, "TEST", Map.of("count", 10), Map.of(), Map.of(), edges);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeTruePath", result.get("nextNodeId"));
    }

    private static EdgeDef edge(String id, String sourceNodeId, String sourcePort, String targetNodeId) {
        EdgeDef e = new EdgeDef();
        e.setId(id);
        e.setSourceNodeId(sourceNodeId);
        e.setSourcePort(sourcePort);
        e.setTargetNodeId(targetNodeId);
        return e;
    }
}
