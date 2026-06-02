package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DslParserTest {

    private final DslParser parser = new DslParser();

    @Test
    void shouldParseValidDsl() {
        String json = """
        {
            "nodes": [
                {"id":"n1","type":"START","position":{"x":100,"y":50}},
                {"id":"n2","type":"DB","position":{"x":100,"y":200},"componentCode":"db_comp","componentId":1},
                {"id":"n3","type":"END","position":{"x":100,"y":400}}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"n1","port":"out"},"target":{"nodeId":"n2","port":"in"}},
                {"id":"e2","source":{"nodeId":"n2","port":"out"},"target":{"nodeId":"n3","port":"in"}}
            ]
        }
        """;
        DslModel model = parser.parse(json);
        assertEquals(3, model.getNodes().size());
        assertEquals(2, model.getEdges().size());
        assertEquals("START", model.getNodeById("n1").getType());
        assertEquals(100.0, model.getNodeById("n1").getPositionX());
        assertEquals(1L, model.getNodeById("n2").getComponentId());
        assertEquals("n1", model.getEdges().get(0).getSourceNodeId());
        assertEquals("n2", model.getEdges().get(0).getTargetNodeId());
    }

    @Test
    void shouldParseGraphWrapper() {
        String json = """
        {"graph": {
            "nodes": [{"id":"n1","type":"START","position":{"x":0,"y":0}}],
            "edges": [{"id":"e1","source":{"nodeId":"n1"},"target":{"nodeId":"n1"}}]
        }}
        """;
        DslModel model = parser.parse(json);
        assertEquals(1, model.getNodes().size());
    }

    @Test
    void shouldRejectNullDsl() {
        assertThrows(com.cqie.datafactory.common.exception.BusinessException.class,
                () -> parser.parse(null));
    }

    @Test
    void shouldRejectBlankDsl() {
        assertThrows(com.cqie.datafactory.common.exception.BusinessException.class,
                () -> parser.parse("  "));
    }

    @Test
    void shouldRejectMissingNodes() {
        String json = "{\"edges\":[]}";
        assertThrows(com.cqie.datafactory.common.exception.BusinessException.class,
                () -> parser.parse(json));
    }

    @Test
    void shouldRejectNodeMissingId() {
        String json = "{\"nodes\":[{\"type\":\"START\"}],\"edges\":[]}";
        assertThrows(com.cqie.datafactory.common.exception.BusinessException.class,
                () -> parser.parse(json));
    }

    @Test
    void shouldHandleLegacyEdgeFormat() {
        String json = """
        {
            "nodes": [{"id":"a","type":"START"},{"id":"b","type":"END"}],
            "edges": [{"id":"e1","from":"a","to":"b"}]
        }
        """;
        DslModel model = parser.parse(json);
        assertEquals("a", model.getEdges().get(0).getSourceNodeId());
        assertEquals("b", model.getEdges().get(0).getTargetNodeId());
    }

    @Test
    void shouldParseIoParams() {
        String json = """
        {
            "nodes": [{
                "id":"n1","type":"DB",
                "inputParams": [{"paramCode":"sql","dataType":"STRING","sourceType":"CONST","sourceValue":"SELECT 1"}],
                "outputParams": [{"paramCode":"rows","dataType":"STRING"}]
            }],
            "edges": []
        }
        """;
        DslModel model = parser.parse(json);
        NodeDef node = model.getNodeById("n1");
        assertEquals(1, node.getInputParams().size());
        assertEquals("sql", node.getInputParams().get(0).getParamCode());
        assertEquals("CONST", node.getInputParams().get(0).getSourceType());
        assertEquals(1, node.getOutputParams().size());
    }
}
