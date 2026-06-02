package com.cqie.datafactory.executor.engine;

import com.cqie.datafactory.executor.engine.plugin.ComponentPlugin;
import com.cqie.datafactory.executor.engine.plugin.PluginContext;
import com.cqie.datafactory.executor.engine.plugin.PluginRegistry;
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
        execEngine = new ExecEngine(pluginRegistry, null);
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
