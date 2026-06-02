package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DslValidatorTest {

    private final DslParser parser = new DslParser();
    private final DslValidator validator = new DslValidator();

    private DslModel validModel;

    @BeforeEach
    void setUp() {
        validModel = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START","position":{"x":100,"y":0}},
                {"id":"db","type":"DB","position":{"x":100,"y":100}},
                {"id":"end","type":"END","position":{"x":100,"y":200}}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"db"}},
                {"id":"e2","source":{"nodeId":"db"},"target":{"nodeId":"end"}}
            ]
        }
        """);
    }

    @Test
    void shouldPassValidDsl() {
        assertDoesNotThrow(() -> validator.validate(validModel));
    }

    @Test
    void shouldRejectEmptyNodes() {
        DslModel model = new DslModel(List.of(), List.of(new EdgeDef()));
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectEmptyEdges() {
        DslModel model = new DslModel(List.of(new NodeDef()), List.of());
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectMissingStart() {
        DslModel model = parser.parse("""
        {
            "nodes": [{"id":"db","type":"DB"},{"id":"end","type":"END"}],
            "edges": [{"id":"e1","source":{"nodeId":"db"},"target":{"nodeId":"end"}}]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectMultipleStarts() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"s1","type":"START"},{"id":"s2","type":"START"},
                {"id":"end","type":"END"}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"s1"},"target":{"nodeId":"end"}},
                {"id":"e2","source":{"nodeId":"s2"},"target":{"nodeId":"end"}}
            ]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectDuplicateNodeId() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"dup","type":"START"},{"id":"dup","type":"END"}
            ],
            "edges": [{"id":"e1","source":{"nodeId":"dup"},"target":{"nodeId":"dup"}}]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectIsolatedNode() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START"},{"id":"db","type":"DB"},
                {"id":"orphan","type":"DB"},{"id":"end","type":"END"}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"db"}},
                {"id":"e2","source":{"nodeId":"db"},"target":{"nodeId":"end"}}
            ]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectEdgeWithNonexistentSource() {
        DslModel model = parser.parse("""
        {
            "nodes": [{"id":"start","type":"START"},{"id":"end","type":"END"}],
            "edges": [{"id":"e1","source":{"nodeId":"ghost"},"target":{"nodeId":"end"}}]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectInvalidDataType() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START","inputParams":[{"paramCode":"p1","dataType":"UNKNOWN","sourceType":"CONST"}]},
                {"id":"end","type":"END"}
            ],
            "edges": [{"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"end"}}]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldRejectInvalidSourceType() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START","inputParams":[{"paramCode":"p1","dataType":"STRING","sourceType":"INVALID"}]},
                {"id":"end","type":"END"}
            ],
            "edges": [{"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"end"}}]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }

    @Test
    void shouldValidateUpstreamParamTypeCompatibility() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START","outputParams":[{"paramCode":"count","dataType":"NUMBER"}]},
                {"id":"db","type":"DB","inputParams":[{"paramCode":"cnt","dataType":"STRING","sourceType":"UPSTREAM_OUTPUT","sourceValue":"start.count"}]},
                {"id":"end","type":"END"}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"db"}},
                {"id":"e2","source":{"nodeId":"db"},"target":{"nodeId":"end"}}
            ]
        }
        """);
        assertThrows(BusinessException.class, () -> validator.validate(model));
    }
}
