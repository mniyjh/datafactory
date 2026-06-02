package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ParamResolverTest {

    private final ParamResolver resolver = new ParamResolver();
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, Map<String, Object>> nodeOutputsMap;

    @BeforeEach
    void setUp() {
        nodeOutputsMap = new HashMap<>();
        nodeOutputsMap.put("upstream", Map.of("name", "Alice", "count", 42));
    }

    @Test
    void shouldResolveConstString() {
        IoParamDef def = makeDef("param1", "CONST", null);
        def.setSourceValue(mapper.createObjectNode().put("value", "hello"));
        Object result = resolver.resolve(def, nodeOutputsMap);
        assertTrue(result instanceof String);
    }

    @Test
    void shouldResolveConstFallbackToDefault() {
        IoParamDef def = makeDef("param1", "CONST", null);
        def.setDefaultValue("defaultVal");
        Object result = resolver.resolve(def, nodeOutputsMap);
        assertEquals("defaultVal", result);
    }

    @Test
    void shouldResolveUpstreamOutputObject() {
        IoParamDef def = makeDef("param1", "UPSTREAM_OUTPUT", null);
        ObjectNode sourceNode = mapper.createObjectNode();
        sourceNode.put("nodeId", "upstream");
        sourceNode.put("paramCode", "name");
        def.setSourceValue(sourceNode);
        Object result = resolver.resolve(def, nodeOutputsMap);
        assertEquals("Alice", result);
    }

    @Test
    void shouldResolveUpstreamOutputDotNotation() {
        IoParamDef def = makeDef("param1", "UPSTREAM_OUTPUT", null);
        def.setSourceValue(mapper.getNodeFactory().textNode("upstream.count"));
        assertEquals(42, resolver.resolve(def, nodeOutputsMap));
    }

    @Test
    void shouldReturnNullForMissingUpstreamNode() {
        IoParamDef def = makeDef("param1", "UPSTREAM_OUTPUT", null);
        ObjectNode sourceNode = mapper.createObjectNode();
        sourceNode.put("nodeId", "nonexistent");
        sourceNode.put("paramCode", "name");
        def.setSourceValue(sourceNode);
        assertNull(resolver.resolve(def, nodeOutputsMap));
    }

    @Test
    void shouldResolveExpressionPlaceholder() {
        IoParamDef def = makeDef("param1", "EXPRESSION", null);
        def.setSourceValue(mapper.createObjectNode().put("expr", "1+1"));
        Object result = resolver.resolve(def, nodeOutputsMap);
        assertTrue(result.toString().startsWith("expr_result("));
    }

    private IoParamDef makeDef(String paramCode, String sourceType, String dataType) {
        IoParamDef def = new IoParamDef();
        def.setParamCode(paramCode);
        def.setSourceType(sourceType);
        def.setDataType(dataType != null ? dataType : "STRING");
        return def;
    }
}
