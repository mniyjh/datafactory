package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PluginRegistryTest {

    private PluginRegistry registry;

    @BeforeEach
    void setUp() {
        ComponentPlugin dbPlugin = new ComponentPlugin() {
            @Override
            public Set<String> supportedTypes() { return Set.of("DB", "MYSQL"); }
            @Override
            public Map<String, Object> execute(PluginContext ctx) { return Map.of("rows", List.of()); }
        };
        ComponentPlugin startPlugin = new ComponentPlugin() {
            @Override
            public Set<String> supportedTypes() { return Set.of("START", "END"); }
            @Override
            public Map<String, Object> execute(PluginContext ctx) { return Map.of(); }
        };
        registry = new PluginRegistry(List.of(dbPlugin, startPlugin));
    }

    @Test
    void shouldGetPluginByExactType() {
        assertNotNull(registry.get("DB"));
        assertNotNull(registry.get("START"));
        assertNotNull(registry.get("END"));
    }

    @Test
    void shouldGetPluginCaseInsensitive() {
        assertNotNull(registry.get("db"));
        assertNotNull(registry.get("Mysql"));
        assertNotNull(registry.get("Start"));
    }

    @Test
    void shouldThrowForUnknownType() {
        assertThrows(BusinessException.class, () -> registry.get("UNKNOWN"));
    }

    @Test
    void shouldThrowForEmptyType() {
        assertThrows(BusinessException.class, () -> registry.get(""));
    }
}
