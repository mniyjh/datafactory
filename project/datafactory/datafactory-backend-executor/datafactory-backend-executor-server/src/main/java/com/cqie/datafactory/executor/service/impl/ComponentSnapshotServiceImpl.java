package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.entity.ComponentField;
import com.cqie.datafactory.configuration.mapper.ComponentFieldMapper;
import com.cqie.datafactory.executor.entity.NodeFieldValue;
import com.cqie.datafactory.executor.entity.NodeInstance;
import com.cqie.datafactory.executor.entity.NodeIoParamValue;
import com.cqie.datafactory.executor.entity.TaskDslEntity;
import com.cqie.datafactory.executor.mapper.NodeFieldValueMapper;
import com.cqie.datafactory.executor.mapper.NodeInstanceMapper;
import com.cqie.datafactory.executor.mapper.NodeIoParamValueMapper;
import com.cqie.datafactory.executor.mapper.TaskDslMapper;
import com.cqie.datafactory.executor.service.ComponentSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComponentSnapshotServiceImpl extends ServiceImpl<NodeInstanceMapper, NodeInstance> implements ComponentSnapshotService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private TaskDslMapper taskDslMapper;
    @Resource
    private ComponentFieldMapper componentFieldMapper;
    @Resource
    private NodeFieldValueMapper nodeFieldValueMapper;
    @Resource
    private NodeIoParamValueMapper nodeIoParamValueMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildNodeSnapshotByTaskDsl(Long taskDslId) {
        TaskDslEntity taskDsl = taskDslMapper.selectById(taskDslId);
        if (taskDsl == null || taskDsl.getDslContent() == null || taskDsl.getDslContent().isBlank()) {
            throw new BusinessException("任务DSL不存在或内容为空");
        }
        try {
            JsonNode root = objectMapper.readTree(taskDsl.getDslContent());
            JsonNode graph = root.get("graph") != null ? root.get("graph") : root;
            JsonNode nodes = graph.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                throw new BusinessException("DSL中未找到节点配置");
            }

            for (JsonNode node : nodes) {
                String nodeId = node.path("id").asText(null);
                if (nodeId == null || nodeId.isBlank()) continue;

                NodeInstance instance = lambdaQuery()
                        .eq(NodeInstance::getTaskDslId, taskDslId)
                        .eq(NodeInstance::getNodeId, nodeId)
                        .one();
                if (instance == null) {
                    instance = new NodeInstance();
                    instance.setTaskDslId(taskDslId);
                    instance.setNodeId(nodeId);
                    instance.setNodeName(node.path("name").asText(nodeId));
                    instance.setNodeType(node.path("type").asText(null));
                    instance.setComponentId(node.path("componentId").asLong(0L));
                    instance.setComponentCode(node.path("componentCode").asText(null));
                    instance.setComponentVersion(node.path("componentVersion").asText(taskDsl.getVersion()));
                    instance.setSyncStatus(0);
                    instance.setDeprecatedConfig(null);
                    instance.setCreatedBy("system");
                    instance.setCreatedTime(LocalDateTime.now());
                    instance.setUpdatedBy("system");
                    instance.setUpdatedTime(LocalDateTime.now());
                    save(instance);
                } else {
                    instance.setComponentId(node.path("componentId").asLong(instance.getComponentId() == null ? 0L : instance.getComponentId()));
                    instance.setComponentCode(node.path("componentCode").asText(instance.getComponentCode()));
                    instance.setComponentVersion(node.path("componentVersion").asText(instance.getComponentVersion()));
                    instance.setNodeName(node.path("name").asText(instance.getNodeName()));
                    instance.setNodeType(node.path("type").asText(instance.getNodeType()));
                    instance.setSyncStatus(0);
                    instance.setUpdatedBy("system");
                    instance.setUpdatedTime(LocalDateTime.now());
                    updateById(instance);
                }

                nodeFieldValueMapper.delete(new LambdaQueryWrapper<NodeFieldValue>().eq(NodeFieldValue::getNodeInstanceId, instance.getId()));
                nodeIoParamValueMapper.delete(new LambdaQueryWrapper<NodeIoParamValue>().eq(NodeIoParamValue::getNodeInstanceId, instance.getId()));

                rebuildFieldValues(instance, node);
                rebuildIoValues(instance, node);
            }
        } catch (Exception e) {
            throw new BusinessException("重建节点快照失败");
        }
    }

    private void rebuildFieldValues(NodeInstance instance, JsonNode node) {
        Long componentId = instance.getComponentId();
        if (componentId == null || componentId <= 0) return;
        List<ComponentField> fields = componentFieldMapper.selectList(new LambdaQueryWrapper<ComponentField>()
                .eq(ComponentField::getComponentId, componentId)
                .orderByAsc(ComponentField::getSortOrder, ComponentField::getId));
        JsonNode valuesNode = node.get("fieldValues");
        int index = 1;
        for (ComponentField field : fields) {
            NodeFieldValue value = new NodeFieldValue();
            value.setNodeInstanceId(instance.getId());
            value.setFieldCode(field.getFieldCode());
            value.setFieldName(field.getFieldName());
            value.setValueType(field.getValueType());
            value.setWidgetType(field.getWidgetType());
            value.setWidgetProps(field.getWidgetProps());
            value.setDefaultValue(field.getDefaultValue());
            value.setRequiredFlag(field.getRequiredFlag());
            value.setDescription(field.getDescription());
            JsonNode v = valuesNode != null ? valuesNode.get(field.getFieldCode()) : null;
            value.setFieldValue(v == null || v.isNull() ? field.getDefaultValue() : v.toString());
            value.setFieldSnapshot(toJson(field));
            value.setSortOrder(index++);
            value.setDeprecatedFlag(0);
            value.setCreatedBy("system");
            value.setCreatedTime(LocalDateTime.now());
            value.setUpdatedBy("system");
            value.setUpdatedTime(LocalDateTime.now());
            nodeFieldValueMapper.insert(value);
        }
    }

    private void rebuildIoValues(NodeInstance instance, JsonNode node) {
        int index = 1;

        // 从 DSL JSON 的 inputParams 直接构建
        JsonNode inputParamsNode = node.get("inputParams");
        if (inputParamsNode != null && inputParamsNode.isArray()) {
            for (JsonNode p : inputParamsNode) {
                String paramCode = p.path("paramCode").asText("");
                if (paramCode.isBlank()) continue;
                NodeIoParamValue value = new NodeIoParamValue();
                value.setNodeInstanceId(instance.getId());
                value.setIoType("INPUT");
                value.setParamCode(paramCode);
                value.setParamName(p.path("paramName").asText(paramCode));
                value.setDataType(p.path("dataType").asText("STRING"));
                value.setSourceType(p.path("sourceType").asText("CONST"));
                value.setSourceValue(p.has("sourceValue") ? p.get("sourceValue").toString() : null);
                value.setParamValue(p.has("defaultValue") ? p.get("defaultValue").toString() : null);
                value.setParamSpace("NODE");
                value.setParamSnapshot(p.toString());
                value.setSortOrder(index++);
                value.setDeprecatedFlag(0);
                value.setCreatedBy("system");
                value.setCreatedTime(LocalDateTime.now());
                value.setUpdatedBy("system");
                value.setUpdatedTime(LocalDateTime.now());
                nodeIoParamValueMapper.insert(value);
            }
        }

        // 从 DSL JSON 的 outputParams 直接构建
        JsonNode outputParamsNode = node.get("outputParams");
        if (outputParamsNode != null && outputParamsNode.isArray()) {
            for (JsonNode p : outputParamsNode) {
                String paramCode = p.path("paramCode").asText("");
                if (paramCode.isBlank()) continue;
                NodeIoParamValue value = new NodeIoParamValue();
                value.setNodeInstanceId(instance.getId());
                value.setIoType("OUTPUT");
                value.setParamCode(paramCode);
                value.setParamName(p.path("paramName").asText(paramCode));
                value.setDataType(p.path("dataType").asText("STRING"));
                value.setSourceType("CONST");
                value.setSourceValue(null);
                value.setParamValue(p.has("defaultValue") ? p.get("defaultValue").toString() : null);
                value.setParamSpace("NODE");
                value.setParamSnapshot(p.toString());
                value.setSortOrder(index++);
                value.setDeprecatedFlag(0);
                value.setCreatedBy("system");
                value.setCreatedTime(LocalDateTime.now());
                value.setUpdatedBy("system");
                value.setUpdatedTime(LocalDateTime.now());
                nodeIoParamValueMapper.insert(value);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
