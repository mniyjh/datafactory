package com.cqie.datafactory.executor.engine;

import com.cqie.datafactory.executor.engine.plugin.ComponentPlugin;
import com.cqie.datafactory.executor.engine.plugin.PluginContext;
import com.cqie.datafactory.executor.engine.plugin.PluginRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExecEngineRetryTest {

    private ExecEngine execEngine;
    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() {
        pluginRegistry = new PluginRegistry(List.of(new FlakyDbPlugin()));
        execEngine = new ExecEngine(pluginRegistry, null, null, null, null);
    }

    @Test
    void shouldRetryOnFailureAndSucceed() {
        String dsl = """
        {
            "graph": {
                "nodes": [
                    {"id":"start","type":"START","name":"Start"},
                    {"id":"db1","type":"DB","name":"DB Step","fieldValues":{"retryCount":"3"}},
                    {"id":"end","type":"END","name":"End"}
                ],
                "edges": [
                    {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"db1"}},
                    {"id":"e2","source":{"nodeId":"db1"},"target":{"nodeId":"end"}}
                ]
            }
        }""";

        List<ExecEngine.NodeExecutionRecord> records = new ArrayList<>();
        Map<String, Object> result = execEngine.execute(dsl, "TEST", null, records::add);

        assertEquals(3, records.stream().filter(r -> "SUCCESS".equals(r.status)).count());
        ExecEngine.NodeExecutionRecord dbRecord = records.stream()
                .filter(r -> "DB".equals(r.nodeType)).findFirst().orElseThrow();
        assertEquals("SUCCESS", dbRecord.status);
        assertTrue(dbRecord.retryCount >= 2, "Should have retried at least 2 times before success");
    }

    @Test
    void shouldNotRetryStartEndNodes() {
        String dsl = """
        {
            "graph": {
                "nodes": [
                    {"id":"start","type":"START","name":"Start"},
                    {"id":"end","type":"END","name":"End"}
                ],
                "edges": [
                    {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"end"}}
                ]
            }
        }""";

        List<ExecEngine.NodeExecutionRecord> records = new ArrayList<>();
        Map<String, Object> result = execEngine.execute(dsl, "TEST", null, records::add);

        ExecEngine.NodeExecutionRecord startRecord = records.stream()
                .filter(r -> "START".equals(r.nodeType)).findFirst().orElseThrow();
        assertEquals("SUCCESS", startRecord.status);
        assertEquals(0, startRecord.retryCount);
    }

    @Test
    void shouldSkipInactiveBranchNodes() {
        pluginRegistry = new PluginRegistry(List.of(new FlakyDbPlugin(), new TestBranchPlugin()));
        execEngine = new ExecEngine(pluginRegistry, null, null, null, null);

        String dsl = """
        {
            "graph": {
                "nodes": [
                    {"id":"start","type":"START","name":"Start"},
                    {"id":"branch1","type":"BRANCH","name":"Branch",
                     "fieldValues":{"expression":"${count} > 5","trueTargetNodeId":"nodeA","falseTargetNodeId":"nodeB"}},
                    {"id":"nodeA","type":"DB","name":"Active Path","fieldValues":{"retryCount":"3"}},
                    {"id":"nodeB","type":"DB","name":"Inactive Path","fieldValues":{"retryCount":"3"}},
                    {"id":"end","type":"END","name":"End"}
                ],
                "edges": [
                    {"id":"e1","source":{"nodeId":"start"},"target":{"nodeId":"branch1"}},
                    {"id":"e2","source":{"nodeId":"branch1","port":"true"},"target":{"nodeId":"nodeA"}},
                    {"id":"e3","source":{"nodeId":"branch1","port":"false"},"target":{"nodeId":"nodeB"}},
                    {"id":"e4","source":{"nodeId":"nodeA"},"target":{"nodeId":"end"}},
                    {"id":"e5","source":{"nodeId":"nodeB"},"target":{"nodeId":"end"}}
                ]
            }
        }""";

        List<ExecEngine.NodeExecutionRecord> records = new ArrayList<>();
        Map<String, Object> trigger = Map.of("count", 10);
        Map<String, Object> result = execEngine.execute(dsl, "TEST", trigger, records::add);

        // Verify records
        ExecEngine.NodeExecutionRecord branchRec = records.stream()
                .filter(r -> "BRANCH".equals(r.nodeType)).findFirst().orElseThrow();
        assertEquals("SUCCESS", branchRec.status);
        assertEquals("nodeA", branchRec.outputs.get("nextNodeId"));

        ExecEngine.NodeExecutionRecord nodeARec = records.stream()
                .filter(r -> "nodeA".equals(r.nodeId)).findFirst().orElseThrow();
        assertEquals("SUCCESS", nodeARec.status);

        ExecEngine.NodeExecutionRecord nodeBRec = records.stream()
                .filter(r -> "nodeB".equals(r.nodeId)).findFirst().orElseThrow();
        assertEquals("SKIPPED", nodeBRec.status);

        // END node should still execute (it has active parent nodeA)
        ExecEngine.NodeExecutionRecord endRec = records.stream()
                .filter(r -> "end".equals(r.nodeId)).findFirst().orElseThrow();
        assertEquals("SUCCESS", endRec.status);
    }

    static class TestBranchPlugin implements ComponentPlugin {
        @Override
        public Set<String> supportedTypes() { return Set.of("BRANCH"); }

        @Override
        public Map<String, Object> execute(PluginContext context) {
            JsonNode fv = context.getNode().getFieldValues();
            String trueTarget = fv != null && fv.has("trueTargetNodeId") ? fv.get("trueTargetNodeId").asText() : "";
            String falseTarget = fv != null && fv.has("falseTargetNodeId") ? fv.get("falseTargetNodeId").asText() : "";
            // Resolve count from resolvedInputs or upstream outputs (like real BranchPlugin)
            Object countObj = context.getResolvedInputs().get("count");
            if (countObj == null) {
                for (Map<String, Object> up : context.getUpstreamOutputs().values()) {
                    if (up.containsKey("count")) { countObj = up.get("count"); break; }
                }
            }
            boolean cond = countObj instanceof Number n ? n.intValue() > 5 : false;
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("conditionResult", cond);
            outputs.put("nextNodeId", cond ? trueTarget : falseTarget);
            return outputs;
        }
    }

    static class FlakyDbPlugin implements ComponentPlugin {
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public Set<String> supportedTypes() { return Set.of("DB", "MYSQL", "DATABASE", "JDBC", "START"); }

        @Override
        public Map<String, Object> execute(PluginContext context) {
            String type = context.getNode().getType();
            // START and END nodes both use the "START" plugin in the engine
            if ("START".equalsIgnoreCase(type) || "END".equalsIgnoreCase(type)) {
                return new HashMap<>(context.getResolvedInputs());
            }
            int count = callCount.incrementAndGet();
            if (count <= 2) {
                throw new RuntimeException("Simulated DB failure #" + count);
            }
            return Map.of("rows", List.of(Map.of("result", "ok")));
        }
    }
}
