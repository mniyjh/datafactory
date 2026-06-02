package com.cqie.datafactory.executor.engine.core.model;

import java.util.ArrayList;
import java.util.List;

public class DslModel {
    private List<NodeDef> nodes = new ArrayList<>();
    private List<EdgeDef> edges = new ArrayList<>();

    public DslModel() {}

    public DslModel(List<NodeDef> nodes, List<EdgeDef> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<NodeDef> getNodes() { return nodes; }
    public void setNodes(List<NodeDef> nodes) { this.nodes = nodes; }

    public List<EdgeDef> getEdges() { return edges; }
    public void setEdges(List<EdgeDef> edges) { this.edges = edges; }

    public NodeDef getNodeById(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }
}
