package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.entity.NodeFieldValue;
import com.cqie.datafactory.executor.entity.NodeInstance;
import com.cqie.datafactory.executor.entity.NodeIoParamValue;
import com.cqie.datafactory.executor.entity.TaskDslEntity;
import com.cqie.datafactory.executor.mapper.NodeFieldValueMapper;
import com.cqie.datafactory.executor.mapper.NodeInstanceMapper;
import com.cqie.datafactory.executor.mapper.NodeIoParamValueMapper;
import com.cqie.datafactory.executor.mapper.TaskDslMapper;
import com.cqie.datafactory.executor.service.NodeSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NodeSyncServiceImpl extends ServiceImpl<NodeInstanceMapper, NodeInstance> implements NodeSyncService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private TaskDslMapper taskDslMapper;
    @Resource
    private NodeFieldValueMapper nodeFieldValueMapper;
    @Resource
    private NodeIoParamValueMapper nodeIoParamValueMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskNodesOutdated(Long componentId, String componentVersion) {
        List<NodeInstance> nodes = lambdaQuery()
                .eq(NodeInstance::getComponentId, componentId)
                .list();
        for (NodeInstance node : nodes) {
            if (componentVersion != null && !componentVersion.equals(node.getComponentVersion())) {
                node.setSyncStatus(1);
                node.setUpdatedBy("system");
                node.setUpdatedTime(LocalDateTime.now());
                updateById(node);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncNodeSnapshot(Long taskDslId) {
        TaskDslEntity taskDsl = taskDslMapper.selectById(taskDslId);
        if (taskDsl == null) {
            throw new BusinessException("任务DSL不存在");
        }
        try {
            JsonNode root = objectMapper.readTree(taskDsl.getDslContent());
            JsonNode graph = root.get("graph") != null ? root.get("graph") : root;
            JsonNode nodesNode = graph.get("nodes");
            if (nodesNode == null || !nodesNode.isArray()) {
                throw new BusinessException("DSL中未找到节点配置");
            }

            for (JsonNode n : nodesNode) {
                String nodeId = n.path("id").asText();
                if (nodeId == null || nodeId.isBlank()) continue;

                NodeInstance instance = lambdaQuery()
                        .eq(NodeInstance::getTaskDslId, taskDslId)
                        .eq(NodeInstance::getNodeId, nodeId)
                        .one();
                if (instance == null) {
                    instance = new NodeInstance();
                    instance.setTaskDslId(taskDslId);
                    instance.setNodeId(nodeId);
                    instance.setNodeName(n.path("name").asText(nodeId));
                    instance.setComponentId(n.path("componentId").asLong(0L));
                    instance.setComponentCode(n.path("componentCode").asText(null));
                    instance.setComponentVersion(n.path("componentVersion").asText(taskDsl.getVersion()));
                    instance.setSyncStatus(0);
                    instance.setDeprecatedConfig(null);
                    instance.setPositionX(parseDecimal(n.path("position").path("x")));
                    instance.setPositionY(parseDecimal(n.path("position").path("y")));
                    instance.setNodeType(n.path("type").asText(null));
                    instance.setDescription(n.path("description").asText(null));
                    instance.setCreatedBy("system");
                    instance.setCreatedTime(LocalDateTime.now());
                    instance.setUpdatedBy("system");
                    instance.setUpdatedTime(LocalDateTime.now());
                    save(instance);
                } else {
                    instance.setNodeName(n.path("name").asText(instance.getNodeName()));
                    instance.setComponentId(n.path("componentId").asLong(instance.getComponentId() == null ? 0L : instance.getComponentId()));
                    instance.setComponentCode(n.path("componentCode").asText(instance.getComponentCode()));
                    instance.setComponentVersion(n.path("componentVersion").asText(instance.getComponentVersion()));
                    BigDecimal x = parseDecimal(n.path("position").path("x"));
                    BigDecimal y = parseDecimal(n.path("position").path("y"));
                    instance.setPositionX(x != null ? x : instance.getPositionX());
                    instance.setPositionY(y != null ? y : instance.getPositionY());
                    instance.setNodeType(n.path("type").asText(instance.getNodeType()));
                    instance.setDescription(n.path("description").asText(instance.getDescription()));
                    instance.setSyncStatus(0);
                    instance.setUpdatedBy("system");
                    instance.setUpdatedTime(LocalDateTime.now());
                    updateById(instance);
                }

                nodeFieldValueMapper.delete(new LambdaQueryWrapper<NodeFieldValue>().eq(NodeFieldValue::getNodeInstanceId, instance.getId()));
                nodeIoParamValueMapper.delete(new LambdaQueryWrapper<NodeIoParamValue>().eq(NodeIoParamValue::getNodeInstanceId, instance.getId()));

                final Long nodeInstanceId = instance.getId();
                JsonNode fieldsNode = n.get("fieldValues");
                if (fieldsNode != null && fieldsNode.isObject()) {
                    java.util.concurrent.atomic.AtomicInteger sort = new java.util.concurrent.atomic.AtomicInteger(1);
                    fieldsNode.fields().forEachRemaining(entry -> {
                        NodeFieldValue value = new NodeFieldValue();
                        value.setNodeInstanceId(nodeInstanceId);
                        value.setFieldCode(entry.getKey());
                        value.setFieldName(entry.getKey());
                        value.setValueType("STRING");
                        value.setFieldValue(entry.getValue().isNull() ? null : entry.getValue().asText());
                        value.setFieldSnapshot(entry.getValue().toString());
                        value.setSortOrder(sort.getAndIncrement());
                        value.setDeprecatedFlag(0);
                        value.setCreatedBy("system");
                        value.setCreatedTime(LocalDateTime.now());
                        value.setUpdatedBy("system");
                        value.setUpdatedTime(LocalDateTime.now());
                        nodeFieldValueMapper.insert(value);
                    });
                }

                JsonNode ioParamsNode = n.get("ioParams");
                if (ioParamsNode != null && ioParamsNode.isObject()) {
                    java.util.concurrent.atomic.AtomicInteger sort = new java.util.concurrent.atomic.AtomicInteger(1);
                    ioParamsNode.fields().forEachRemaining(entry -> {
                        JsonNode io = entry.getValue();
                        NodeIoParamValue value = new NodeIoParamValue();
                        value.setNodeInstanceId(nodeInstanceId);
                        value.setIoType(io.path("paramType").asText("INPUT"));
                        value.setParamCode(entry.getKey());
                        value.setParamName(entry.getKey());
                        value.setDataType(io.path("dataType").asText("STRING"));
                        value.setSourceType(io.path("sourceType").asText(null));
                        value.setSourceValue(io.path("value").asText(null));
                        value.setParamValue(io.path("value").asText(null));
                        value.setParamSpace(io.path("paramSpace").asText(null));
                        value.setParamSnapshot(io.toString());
                        value.setSortOrder(sort.getAndIncrement());
                        value.setDeprecatedFlag(0);
                        value.setCreatedBy("system");
                        value.setCreatedTime(LocalDateTime.now());
                        value.setUpdatedBy("system");
                        value.setUpdatedTime(LocalDateTime.now());
                        nodeIoParamValueMapper.insert(value);
                    });
                }
            }
        } catch (Exception e) {
            throw new BusinessException("同步节点快照失败");
        }
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
