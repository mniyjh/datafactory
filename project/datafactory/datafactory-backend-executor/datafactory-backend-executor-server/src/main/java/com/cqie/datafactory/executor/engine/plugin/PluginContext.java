package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef;

import java.util.HashMap;
import java.util.Map;

public class PluginContext {
    private final NodeDef node;
    private final String environment;
    private final Map<String, Object> resolvedInputs;
    private final Map<String, Map<String, Object>> upstreamOutputs;
    private final Map<String, Object> resolvedVars;

    public PluginContext(NodeDef node, String environment,
                         Map<String, Object> resolvedInputs,
                         Map<String, Map<String, Object>> upstreamOutputs,
                         Map<String, Object> resolvedVars) {
        this.node = node;
        this.environment = environment;
        this.resolvedInputs = resolvedInputs;
        this.upstreamOutputs = upstreamOutputs;
        this.resolvedVars = resolvedVars;
    }

    public NodeDef getNode() { return node; }
    public String getEnvironment() { return environment; }
    public Map<String, Object> getResolvedInputs() { return resolvedInputs; }
    public Map<String, Map<String, Object>> getUpstreamOutputs() { return upstreamOutputs; }
    public Map<String, Object> getResolvedVars() { return resolvedVars != null ? resolvedVars : new HashMap<>(); }
}
