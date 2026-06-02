package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class DslParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public DslModel parse(String dslContent) {
        if (dslContent == null || dslContent.isBlank()) {
            throw new BusinessException("DSL内容不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(dslContent);
            JsonNode graphRoot = resolveGraphRoot(root);

            List<NodeDef> nodes = parseNodes(graphRoot);
            List<EdgeDef> edges = parseEdges(graphRoot);
            return new DslModel(nodes, edges);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("DSL解析失败: " + e.getMessage());
        }
    }

    private JsonNode resolveGraphRoot(JsonNode root) {
        JsonNode graph = root.get("graph");
        return (graph != null && graph.isObject()) ? graph : root;
    }

    private List<NodeDef> parseNodes(JsonNode root) {
        JsonNode nodesNode = root.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            throw new BusinessException("DSL缺少nodes配置");
        }
        List<NodeDef> nodes = new ArrayList<>();
        for (JsonNode n : nodesNode) {
            NodeDef node = new NodeDef();
            node.setId(requireTextField(n, "id", "节点ID不能为空"));
            node.setType(requireTextField(n, "type", "节点类型不能为空"));
            node.setName(readTextField(n, "name"));
            node.setLabel(readTextField(n, "label"));
            node.setComponentCode(readTextField(n, "componentCode"));
            if (n.has("componentId") && n.get("componentId").canConvertToLong()) {
                node.setComponentId(n.get("componentId").asLong());
            }
            JsonNode pos = n.get("position");
            if (pos != null) {
                if (pos.has("x") && pos.get("x").isNumber()) node.setPositionX(pos.get("x").asDouble());
                if (pos.has("y") && pos.get("y").isNumber()) node.setPositionY(pos.get("y").asDouble());
            }
            node.setFieldValues(n.get("fieldValues"));
            node.setInputParams(parseIoParams(n, "inputParams", "INPUT"));
            node.setOutputParams(parseIoParams(n, "outputParams", "OUTPUT"));
            node.setRaw(n);
            nodes.add(node);
        }
        return nodes;
    }

    private List<IoParamDef> parseIoParams(JsonNode node, String field, String defaultParamType) {
        List<IoParamDef> result = new ArrayList<>();
        JsonNode params = node.get(field);
        if (params != null && params.isArray()) {
            for (JsonNode p : params) {
                IoParamDef def = new IoParamDef();
                def.setParamCode(p.path("paramCode").asText());
                def.setParamName(p.path("paramName").asText());
                def.setParamType(p.has("paramType") ? p.get("paramType").asText() : defaultParamType);
                def.setDataType(p.path("dataType").asText("STRING").toUpperCase());
                def.setSourceType(p.path("sourceType").asText("CONST").toUpperCase());
                def.setSourceValue(p.get("sourceValue"));
                def.setDefaultValue(readTextField(p, "defaultValue"));
                def.setRequiredFlag(p.path("requiredFlag").asInt(0));
                def.setParamSpace(readTextField(p, "paramSpace"));
                result.add(def);
            }
        }
        return result;
    }

    private List<EdgeDef> parseEdges(JsonNode root) {
        JsonNode edgesNode = root.get("edges");
        if (edgesNode == null || !edgesNode.isArray()) {
            return new ArrayList<>();
        }
        List<EdgeDef> edges = new ArrayList<>();
        for (JsonNode e : edgesNode) {
            EdgeDef edge = new EdgeDef();
            edge.setId(requireTextField(e, "id", "连线ID不能为空"));
            edge.setSourceNodeId(resolveEdgeNodeId(e, "source", "from"));
            edge.setSourcePort(resolveEdgePort(e, "source", "out"));
            edge.setTargetNodeId(resolveEdgeNodeId(e, "target", "to"));
            edge.setTargetPort(resolveEdgePort(e, "target", "in"));
            edges.add(edge);
        }
        return edges;
    }

    public String resolveEdgeNodeId(JsonNode edge, String primaryField, String legacyField) {
        JsonNode nested = edge.get(primaryField);
        if (nested != null && nested.isObject()) {
            JsonNode nodeId = nested.get("nodeId");
            if (nodeId == null || nodeId.isNull() || nodeId.asText().isBlank()) {
                nodeId = nested.get("id");
            }
            if (nodeId != null && !nodeId.isNull() && !nodeId.asText().isBlank()) {
                return nodeId.asText();
            }
        }
        JsonNode legacy = edge.get(legacyField);
        return (legacy != null && !legacy.isNull()) ? legacy.asText() : null;
    }

    private String resolveEdgePort(JsonNode edge, String primaryField, String defaultPort) {
        JsonNode nested = edge.get(primaryField);
        if (nested != null && nested.isObject()) {
            JsonNode portNode = nested.get("port");
            if (portNode != null && !portNode.isNull() && !portNode.asText().isBlank()) {
                return portNode.asText();
            }
        }
        return defaultPort;
    }

    private String requireTextField(JsonNode node, String fieldName, String errorMessage) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().isBlank()) {
            throw new BusinessException(errorMessage);
        }
        return fieldNode.asText();
    }

    private String readTextField(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
    }
}
