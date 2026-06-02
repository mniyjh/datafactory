package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;

import java.util.*;

public class DslValidator {

    private static final Set<String> ALLOWED_DATA_TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "JSON");
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("CONST", "UPSTREAM_OUTPUT", "EXPRESSION");

    private final List<DslValidationRule> rules = new ArrayList<>();

    public DslValidator() {
        rules.add(this::validateStructure);
        rules.add(this::validateStartEnd);
        rules.add(this::validateNodeIds);
        rules.add(this::validateEdges);
        rules.add(this::validateConnectivity);
        rules.add(this::validateIoParams);
        rules.add(this::validateParamCompatibility);
    }

    public void validate(DslModel dsl) {
        for (DslValidationRule rule : rules) {
            rule.validate(dsl);
        }
    }

    private void validateStructure(DslModel dsl) {
        if (dsl.getNodes().isEmpty()) {
            throw new BusinessException("DSL缺少nodes配置");
        }
        if (dsl.getEdges().isEmpty()) {
            throw new BusinessException("DSL缺少edges配置");
        }
    }

    private void validateStartEnd(DslModel dsl) {
        long startCount = dsl.getNodes().stream()
                .filter(n -> "START".equalsIgnoreCase(n.getType())).count();
        long endCount = dsl.getNodes().stream()
                .filter(n -> "END".equalsIgnoreCase(n.getType())).count();
        if (startCount != 1 || endCount != 1) {
            throw new BusinessException("任务配置必须包含且仅包含一个START节点和一个END节点");
        }
    }

    private void validateNodeIds(DslModel dsl) {
        Set<String> nodeIds = new HashSet<>();
        for (NodeDef n : dsl.getNodes()) {
            if (!nodeIds.add(n.getId())) {
                throw new BusinessException("节点ID重复: " + n.getId());
            }
        }
    }

    private void validateEdges(DslModel dsl) {
        Set<String> nodeIds = new HashSet<>();
        dsl.getNodes().forEach(n -> nodeIds.add(n.getId()));

        Set<String> edgeIds = new HashSet<>();
        Set<String> edgeKeys = new HashSet<>();
        for (EdgeDef e : dsl.getEdges()) {
            if (e.getSourceNodeId() == null || e.getSourceNodeId().isBlank()) {
                throw new BusinessException("连线起点不能为空");
            }
            if (e.getTargetNodeId() == null || e.getTargetNodeId().isBlank()) {
                throw new BusinessException("连线终点不能为空");
            }
            if (!edgeIds.add(e.getId())) {
                throw new BusinessException("连线ID重复: " + e.getId());
            }
            if (!nodeIds.contains(e.getSourceNodeId())) {
                throw new BusinessException("连线起点不存在: " + e.getSourceNodeId());
            }
            if (!nodeIds.contains(e.getTargetNodeId())) {
                throw new BusinessException("连线终点不存在: " + e.getTargetNodeId());
            }
            String edgeKey = e.getSourceNodeId() + ":" + e.getSourcePort()
                    + "->" + e.getTargetNodeId() + ":" + e.getTargetPort();
            if (!edgeKeys.add(edgeKey)) {
                throw new BusinessException("重复连线: " + edgeKey);
            }
        }
    }

    private void validateConnectivity(DslModel dsl) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        dsl.getNodes().forEach(n -> {
            inDegree.put(n.getId(), 0);
            outDegree.put(n.getId(), 0);
        });
        for (EdgeDef e : dsl.getEdges()) {
            outDegree.put(e.getSourceNodeId(), outDegree.get(e.getSourceNodeId()) + 1);
            inDegree.put(e.getTargetNodeId(), inDegree.get(e.getTargetNodeId()) + 1);
        }
        for (NodeDef n : dsl.getNodes()) {
            if (inDegree.get(n.getId()) == 0 && outDegree.get(n.getId()) == 0) {
                throw new BusinessException("节点存在孤立点: " + n.getId());
            }
        }
    }

    private void validateIoParams(DslModel dsl) {
        for (NodeDef n : dsl.getNodes()) {
            Set<String> inputCodes = new HashSet<>();
            for (IoParamDef p : n.getInputParams()) {
                if (p.getParamCode() == null || p.getParamCode().isBlank()) {
                    throw new BusinessException("节点参数编码为空: " + n.getId());
                }
                if (!ALLOWED_DATA_TYPES.contains(p.getDataType())) {
                    throw new BusinessException("节点参数数据类型不支持: " + p.getDataType());
                }
                if (!ALLOWED_SOURCE_TYPES.contains(p.getSourceType())) {
                    throw new BusinessException("节点参数来源不支持: " + p.getSourceType());
                }
                if (!inputCodes.add(p.getParamCode())) {
                    throw new BusinessException("节点参数编码重复: " + n.getId() + "." + p.getParamCode());
                }
            }
        }
    }

    private void validateParamCompatibility(DslModel dsl) {
        Map<String, Set<String>> upstreamMap = new HashMap<>();
        for (EdgeDef e : dsl.getEdges()) {
            upstreamMap.computeIfAbsent(e.getTargetNodeId(), k -> new HashSet<>()).add(e.getSourceNodeId());
        }

        for (NodeDef node : dsl.getNodes()) {
            Set<String> validUpstreamNodes = upstreamMap.getOrDefault(node.getId(), Set.of());
            for (IoParamDef p : node.getInputParams()) {
                if ("UPSTREAM_OUTPUT".equals(p.getSourceType()) && p.getSourceValue() != null) {
                    String refNodeId = null;
                    String refParamCode = null;
                    if (p.getSourceValue().isObject()) {
                        refNodeId = p.getSourceValue().path("nodeId").asText(null);
                        refParamCode = p.getSourceValue().path("paramCode").asText(null);
                    } else if (p.getSourceValue().isTextual()) {
                        String text = p.getSourceValue().asText();
                        if (text.contains(".")) {
                            String[] parts = text.split("\\.", 2);
                            refNodeId = parts[0];
                            refParamCode = parts.length > 1 ? parts[1] : null;
                        }
                    }
                    if (refNodeId != null && !validUpstreamNodes.contains(refNodeId)) {
                        throw new BusinessException("上游输出引用节点不在直连上游中: " + refNodeId);
                    }
                    if (refNodeId != null && refParamCode != null) {
                        NodeDef upstream = dsl.getNodeById(refNodeId);
                        if (upstream != null) {
                            String finalRefParamCode = refParamCode;
                            boolean found = upstream.getOutputParams().stream()
                                    .anyMatch(o -> finalRefParamCode.equals(o.getParamCode())
                                            && p.getDataType().equals(o.getDataType()));
                            if (!found) {
                                throw new BusinessException("上游参数类型不匹配或不存在: "
                                        + refNodeId + "." + refParamCode);
                            }
                        }
                    }
                }
            }
        }
    }
}
