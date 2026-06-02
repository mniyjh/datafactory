package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
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
}
