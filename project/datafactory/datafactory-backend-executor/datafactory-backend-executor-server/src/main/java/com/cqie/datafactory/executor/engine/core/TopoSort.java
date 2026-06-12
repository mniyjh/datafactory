package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TopoSort {

    private static final Logger log = LoggerFactory.getLogger(TopoSort.class);

    /**
     * 层级拓扑排序 — 同层节点无依赖关系，可并行执行
     * <p>
     * 使用 BFS 分层：从入度为 0 的节点开始，逐层移除已处理节点，
     * 每层中的节点没有相互依赖，可以安全地并行执行。
     *
     * @return 按层级分组的节点列表，第 0 层为 START 节点，最后一层为 END 节点
     */
    public List<List<NodeDef>> layeredSort(DslModel model) {
        Map<String, NodeDef> nodeMap = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (NodeDef n : model.getNodes()) {
            nodeMap.put(n.getId(), n);
            adjacency.put(n.getId(), new ArrayList<>());
            inDegree.put(n.getId(), 0);
        }

        for (EdgeDef e : model.getEdges()) {
            String from = e.getSourceNodeId();
            String to = e.getTargetNodeId();
            if (adjacency.containsKey(from) && adjacency.containsKey(to)) {
                adjacency.get(from).add(to);
                inDegree.put(to, inDegree.get(to) + 1);
            }
        }

        List<List<NodeDef>> layers = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        // Find all nodes with in-degree 0 (typically START nodes)
        for (NodeDef n : model.getNodes()) {
            if (inDegree.get(n.getId()) == 0) {
                queue.offer(n.getId());
            }
        }

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<NodeDef> layer = new ArrayList<>();

            for (int i = 0; i < levelSize; i++) {
                String nodeId = queue.poll();
                layer.add(nodeMap.get(nodeId));

                for (String neighbor : adjacency.getOrDefault(nodeId, Collections.emptyList())) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        queue.offer(neighbor);
                    }
                }
            }

            if (!layer.isEmpty()) {
                layers.add(layer);
            }
        }

        // If cycle detected, fall back to flat sort wrapped in single layer
        int totalNodes = layers.stream().mapToInt(List::size).sum();
        if (totalNodes != nodeMap.size()) {
            log.warn("Cycle detected in DAG, falling back to single-layer execution");
            return Collections.singletonList(sort(model));
        }

        return layers;
    }

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
