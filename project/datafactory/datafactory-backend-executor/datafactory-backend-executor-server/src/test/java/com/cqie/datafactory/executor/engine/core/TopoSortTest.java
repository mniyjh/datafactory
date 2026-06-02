package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TopoSortTest {

    private final DslParser parser = new DslParser();
    private final TopoSort sorter = new TopoSort();

    @Test
    void shouldSortLinearDag() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"n1","type":"START","position":{"x":100,"y":0}},
                {"id":"n2","type":"DB","position":{"x":100,"y":100}},
                {"id":"n3","type":"END","position":{"x":100,"y":200}}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"n1"},"target":{"nodeId":"n2"}},
                {"id":"e2","source":{"nodeId":"n2"},"target":{"nodeId":"n3"}}
            ]
        }
        """);
        List<NodeDef> sorted = sorter.sort(model);
        assertEquals(3, sorted.size());
        assertEquals("n1", sorted.get(0).getId());
        assertEquals("n2", sorted.get(1).getId());
        assertEquals("n3", sorted.get(2).getId());
    }

    @Test
    void shouldSortBranchingDag() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START","position":{"x":100,"y":0}},
                {"id":"left","type":"DB","position":{"x":50,"y":100}},
                {"id":"right","type":"API","position":{"x":150,"y":100}},
                {"id":"end","type":"END","position":{"x":100,"y":200}}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"left"}},
                {"id":"e2","source":{"nodeId":"start"},"target":{"nodeId":"right"}},
                {"id":"e3","source":{"nodeId":"left"},"target":{"nodeId":"end"}},
                {"id":"e4","source":{"nodeId":"right"},"target":{"nodeId":"end"}}
            ]
        }
        """);
        List<NodeDef> sorted = sorter.sort(model);
        assertEquals(4, sorted.size());
        assertEquals("start", sorted.get(0).getId());
        assertEquals("end", sorted.get(3).getId());
    }

    @Test
    void shouldRespectCanvasOrderForSameLevel() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"start","type":"START","position":{"x":100,"y":0}},
                {"id":"top","type":"DB","position":{"x":100,"y":50}},
                {"id":"bottom","type":"DB","position":{"x":100,"y":150}},
                {"id":"end","type":"END","position":{"x":100,"y":200}}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"top"}},
                {"id":"e2","source":{"nodeId":"start"},"target":{"nodeId":"bottom"}},
                {"id":"e3","source":{"nodeId":"top"},"target":{"nodeId":"end"}},
                {"id":"e4","source":{"nodeId":"bottom"},"target":{"nodeId":"end"}}
            ]
        }
        """);
        List<NodeDef> sorted = sorter.sort(model);
        assertEquals("start", sorted.get(0).getId());
        assertEquals("end", sorted.get(3).getId());
        // top (y=50) should come before bottom (y=150)
        assertEquals("top", sorted.get(1).getId());
        assertEquals("bottom", sorted.get(2).getId());
    }

    @Test
    void shouldReturnAllNodesEvenWithCycle() {
        DslModel model = parser.parse("""
        {
            "nodes": [
                {"id":"a","type":"DB","position":{"x":0,"y":0}},
                {"id":"b","type":"DB","position":{"x":0,"y":100}},
                {"id":"c","type":"DB","position":{"x":0,"y":200}}
            ],
            "edges": [
                {"id":"e1","source":{"nodeId":"a"},"target":{"nodeId":"b"}},
                {"id":"e2","source":{"nodeId":"b"},"target":{"nodeId":"c"}},
                {"id":"e3","source":{"nodeId":"c"},"target":{"nodeId":"a"}}
            ]
        }
        """);
        List<NodeDef> sorted = sorter.sort(model);
        assertEquals(3, sorted.size());
    }
}
