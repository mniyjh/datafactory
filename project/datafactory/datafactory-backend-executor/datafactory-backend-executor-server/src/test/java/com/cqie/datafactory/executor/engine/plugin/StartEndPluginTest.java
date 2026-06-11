package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StartEndPluginTest {

    private StartEndPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StartEndPlugin();
    }

    // ───────── START node ─────────

    @Test
    void startShouldReturnTriggerParamsWhenNoOutputParamsDefined() {
        NodeDef node = startNode("s1", null, null);

        Map<String, Object> resolvedInputs = Map.of("a", "10", "b", "3");
        PluginContext ctx = new PluginContext(node, "DEV", resolvedInputs, Map.of(), Map.of());

        Map<String, Object> result = plugin.execute(ctx);

        assertEquals("10", result.get("a"));
        assertEquals("3", result.get("b"));
    }

    @Test
    void startShouldFilterOutputByParamsWhenDefined() {
        NodeDef node = startNode("s1",
                null,                                    // inputParams 为空（trigger params 直接入 resolvedInputs）
                List.of(ioDef("a", "10")));                // 只暴露 a

        Map<String, Object> resolvedInputs = Map.of("a", "5", "b", "7");
        PluginContext ctx = new PluginContext(node, "DEV", resolvedInputs, Map.of(), Map.of());

        Map<String, Object> result = plugin.execute(ctx);

        assertEquals("5", result.get("a"));
        assertNull(result.get("b"), "b 不在 outputParams 中，应被过滤");
    }

    @Test
    void startShouldUseDefaultWhenNoInputProvided() {
        NodeDef node = startNode("s1",
                null,                                    // 无 inputParams
                List.of(ioDef("a", "99")));               // outputParam 默认值=99

        PluginContext ctx = new PluginContext(node, "DEV", Map.of(), Map.of(), Map.of());

        Map<String, Object> result = plugin.execute(ctx);
        assertEquals("99", result.get("a"));
    }

    // ───────── END node ─────────

    @Test
    void endShouldAggregateAllUpstreamOutputs() {
        NodeDef node = endNode("e1", null);

        // 模拟 ExecEngine 的行为：把所有上游输出合并到 resolvedInputs（END 节点特有逻辑在 ExecEngine 第108行）
        Map<String, Object> mergedInputs = new HashMap<>();
        mergedInputs.put("a", "10");
        mergedInputs.put("sum", "13");
        mergedInputs.put("product", "30");

        PluginContext ctx = new PluginContext(node, "DEV", mergedInputs, Map.of(), Map.of());

        Map<String, Object> result = plugin.execute(ctx);

        // END 无 outputParams → outputs.isEmpty() → 兜底 putAll(resolvedInputs)
        assertEquals(3, result.size());
        assertEquals("10", result.get("a"));
        assertEquals("13", result.get("sum"));
    }

    @Test
    void endShouldFilterOutputByParamsWhenDefined() {
        NodeDef node = endNode("e1", List.of(ioDef("sum", null), ioDef("product", null)));

        Map<String, Map<String, Object>> upstream = Map.of(
                "s1", Map.of("a", "10"),
                "py1", Map.of("sum", "13", "product", "30")
        );

        // Simulating ExecEngine's END aggregation: put all upstream into resolvedInputs
        Map<String, Object> resolvedInputs = new HashMap<>();
        upstream.forEach((k, v) -> resolvedInputs.putAll(v));

        PluginContext ctx = new PluginContext(node, "DEV", resolvedInputs, upstream, Map.of());

        Map<String, Object> result = plugin.execute(ctx);

        assertTrue(result.containsKey("sum"));
        assertTrue(result.containsKey("product"));
        assertFalse(result.containsKey("a"), "a 不在 END outputParams，应被过滤");
    }

    // ───────── helpers ─────────

    private NodeDef startNode(String id, List<NodeDef.IoParamDef> inputParams, List<NodeDef.IoParamDef> outputParams) {
        return buildNode(id, "START", inputParams, outputParams);
    }

    private NodeDef endNode(String id, List<NodeDef.IoParamDef> outputParams) {
        return buildNode(id, "END", null, outputParams);
    }

    private NodeDef buildNode(String id, String type, List<NodeDef.IoParamDef> inputParams, List<NodeDef.IoParamDef> outputParams) {
        NodeDef node = new NodeDef();
        node.setId(id);
        node.setType(type);
        node.setName(type);
        if (inputParams != null) node.setInputParams(inputParams);
        if (outputParams != null) node.setOutputParams(outputParams);
        return node;
    }

    private NodeDef.IoParamDef ioDef(String paramCode, String defaultValue) {
        NodeDef.IoParamDef def = new NodeDef.IoParamDef();
        def.setParamCode(paramCode);
        def.setParamName(paramCode);
        def.setDefaultValue(defaultValue);
        return def;
    }
}
