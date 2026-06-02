package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;

import java.util.*;

public class TopoSort {

    public List<NodeDef> sort(DslModel dsl) {
        Map<String, NodeDef> nodeMap = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (NodeDef n : dsl.getNodes()) {
            nodeMap.put(n.getId(), n);
            adj.put(n.getId(), new ArrayList<>());
            inDegree.put(n.getId(), 0);
        }

        for (EdgeDef e : dsl.getEdges()) {
            String from = e.getSourceNodeId();
            String to = e.getTargetNodeId();
            if (adj.containsKey(from) && adj.containsKey(to)) {
                adj.get(from).add(to);
                inDegree.put(to, inDegree.get(to) + 1);
            }
        }

        Comparator<String> canvasOrder = Comparator
                .comparingDouble((String id) -> {
                    NodeDef n = nodeMap.get(id);
                    return n != null && n.getPositionY() != null ? n.getPositionY() : Double.MAX_VALUE;
                })
                .thenComparingDouble(id -> {
                    NodeDef n = nodeMap.get(id);
                    return n != null && n.getPositionX() != null ? n.getPositionX() : Double.MAX_VALUE;
                })
                .thenComparing(id -> id);

        PriorityQueue<String> queue = new PriorityQueue<>(canvasOrder);
        inDegree.forEach((id, degree) -> {
            if (degree == 0) queue.add(id);
        });

        List<NodeDef> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            result.add(nodeMap.get(curr));
            List<String> neighbors = new ArrayList<>(adj.get(curr));
            neighbors.sort(canvasOrder);
            for (String neighbor : neighbors) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) queue.add(neighbor);
            }
        }

        if (result.size() == nodeMap.size()) return result;

        // Fallback: cycle detected, sort by canvas position
        List<NodeDef> fallback = new ArrayList<>(dsl.getNodes());
        fallback.sort(Comparator
                .comparingDouble((NodeDef n) -> n.getPositionY() != null ? n.getPositionY() : Double.MAX_VALUE)
                .thenComparingDouble(n -> n.getPositionX() != null ? n.getPositionX() : Double.MAX_VALUE)
                .thenComparing(NodeDef::getId));
        return fallback;
    }
}
