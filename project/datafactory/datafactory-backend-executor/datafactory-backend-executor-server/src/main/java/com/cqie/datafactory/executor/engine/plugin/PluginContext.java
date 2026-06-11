package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;

import java.util.*;

public class PluginContext {
    private final NodeDef node;
    private final String environment;
    private final Map<String, Object> resolvedInputs;
    private final Map<String, Map<String, Object>> upstreamOutputs;
    private final Map<String, Object> resolvedVars;
    private final List<EdgeDef> outgoingEdges;

    public PluginContext(NodeDef node, String environment,
                         Map<String, Object> resolvedInputs,
                         Map<String, Map<String, Object>> upstreamOutputs,
                         Map<String, Object> resolvedVars) {
        this(node, environment, resolvedInputs, upstreamOutputs, resolvedVars, List.of());
    }

    public PluginContext(NodeDef node, String environment,
                         Map<String, Object> resolvedInputs,
                         Map<String, Map<String, Object>> upstreamOutputs,
                         Map<String, Object> resolvedVars,
                         List<EdgeDef> outgoingEdges) {
        this.node = node;
        this.environment = environment;
        this.resolvedInputs = resolvedInputs;
        this.upstreamOutputs = upstreamOutputs;
        this.resolvedVars = resolvedVars;
        this.outgoingEdges = outgoingEdges != null ? outgoingEdges : List.of();
    }

    public NodeDef getNode() { return node; }
    public String getEnvironment() { return environment; }
    public Map<String, Object> getResolvedInputs() { return resolvedInputs; }
    public Map<String, Map<String, Object>> getUpstreamOutputs() { return upstreamOutputs; }
    public Map<String, Object> getResolvedVars() { return resolvedVars != null ? resolvedVars : new HashMap<>(); }
    /** 当前节点的所有出边（按 sourcePort 排序），BRANCH 等路由节点可用此动态获取下游 */
    public List<EdgeDef> getOutgoingEdges() { return outgoingEdges; }
}
