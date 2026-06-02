package com.cqie.datafactory.executor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.entity.NodeFieldValue;
import com.cqie.datafactory.executor.entity.NodeInstance;
import com.cqie.datafactory.executor.entity.NodeIoParamValue;
import com.cqie.datafactory.executor.mapper.NodeInstanceMapper;
import com.cqie.datafactory.executor.service.NodeFieldValueService;
import com.cqie.datafactory.executor.service.NodeIoParamValueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/task")
public class NodeValueController {

    private final NodeFieldValueService nodeFieldValueService;
    private final NodeIoParamValueService nodeIoParamValueService;
    private final NodeInstanceMapper nodeInstanceMapper;

    public NodeValueController(NodeFieldValueService nodeFieldValueService,
                               NodeIoParamValueService nodeIoParamValueService,
                               NodeInstanceMapper nodeInstanceMapper) {
        this.nodeFieldValueService = nodeFieldValueService;
        this.nodeIoParamValueService = nodeIoParamValueService;
        this.nodeInstanceMapper = nodeInstanceMapper;
    }

    @GetMapping("/{taskId}/node/{nodeId}/fields")
    public Result<List<NodeFieldValue>> fields(@PathVariable("taskId") Long taskId,
                                               @PathVariable("nodeId") String nodeId) {
        NodeInstance instance = nodeInstanceMapper.selectOne(new LambdaQueryWrapper<NodeInstance>()
                .eq(NodeInstance::getTaskDslId, taskId)
                .eq(NodeInstance::getNodeId, nodeId));
        if (instance == null) {
            return Result.success(List.of());
        }
        return Result.success(nodeFieldValueService.lambdaQuery()
                .eq(NodeFieldValue::getNodeInstanceId, instance.getId())
                .orderByAsc(NodeFieldValue::getSortOrder, NodeFieldValue::getId)
                .list());
    }

    @PutMapping("/{taskId}/node/{nodeId}/fields")
    public Result<Void> saveFields(@PathVariable("taskId") Long taskId,
                                   @PathVariable("nodeId") String nodeId,
                                   @RequestParam(value = "componentId", required = false) Long componentId,
                                   @RequestParam(value = "nodeName", required = false) String nodeName,
                                   @RequestParam(value = "nodeType", required = false) String nodeType,
                                   @RequestBody List<NodeFieldValue> values) {
        NodeInstance instance = nodeInstanceMapper.selectOne(new LambdaQueryWrapper<NodeInstance>()
                .eq(NodeInstance::getTaskDslId, taskId)
                .eq(NodeInstance::getNodeId, nodeId));
        if (instance == null) {
            instance = createNodeInstance(taskId, nodeId, componentId, nodeName, nodeType);
        }
        nodeFieldValueService.remove(new LambdaQueryWrapper<NodeFieldValue>()
                .eq(NodeFieldValue::getNodeInstanceId, instance.getId()));
        for (NodeFieldValue value : values) {
            value.setNodeInstanceId(instance.getId());
        }
        nodeFieldValueService.saveBatch(values);
        return Result.success();
    }

    private NodeInstance createNodeInstance(Long taskDslId, String nodeId, Long componentId, String nodeName, String nodeType) {
        NodeInstance instance = new NodeInstance();
        instance.setTaskDslId(taskDslId);
        instance.setNodeId(nodeId);
        instance.setNodeName(nodeName != null ? nodeName : nodeId);
        instance.setNodeType(nodeType);
        instance.setComponentId(componentId);
        instance.setSyncStatus(0);
        instance.setCreatedBy("system");
        instance.setCreatedTime(LocalDateTime.now());
        instance.setUpdatedBy("system");
        instance.setUpdatedTime(LocalDateTime.now());
        nodeInstanceMapper.insert(instance);
        return instance;
    }

    @GetMapping("/{taskId}/node/{nodeId}/io-params")
    public Result<List<NodeIoParamValue>> ioParams(@PathVariable("taskId") Long taskId,
                                                   @PathVariable("nodeId") String nodeId) {
        NodeInstance instance = nodeInstanceMapper.selectOne(new LambdaQueryWrapper<NodeInstance>()
                .eq(NodeInstance::getTaskDslId, taskId)
                .eq(NodeInstance::getNodeId, nodeId));
        if (instance == null) {
            return Result.success(List.of());
        }
        return Result.success(nodeIoParamValueService.lambdaQuery()
                .eq(NodeIoParamValue::getNodeInstanceId, instance.getId())
                .orderByAsc(NodeIoParamValue::getSortOrder, NodeIoParamValue::getId)
                .list());
    }

    @PutMapping("/{taskId}/node/{nodeId}/io-params")
    public Result<Void> saveIoParams(@PathVariable("taskId") Long taskId,
                                     @PathVariable("nodeId") String nodeId,
                                     @RequestParam(value = "componentId", required = false) Long componentId,
                                     @RequestParam(value = "nodeName", required = false) String nodeName,
                                     @RequestParam(value = "nodeType", required = false) String nodeType,
                                     @RequestBody List<NodeIoParamValue> values) {
        NodeInstance instance = nodeInstanceMapper.selectOne(new LambdaQueryWrapper<NodeInstance>()
                .eq(NodeInstance::getTaskDslId, taskId)
                .eq(NodeInstance::getNodeId, nodeId));
        if (instance == null) {
            instance = createNodeInstance(taskId, nodeId, componentId, nodeName, nodeType);
        }
        nodeIoParamValueService.remove(new LambdaQueryWrapper<NodeIoParamValue>()
                .eq(NodeIoParamValue::getNodeInstanceId, instance.getId()));
        for (NodeIoParamValue value : values) {
            value.setNodeInstanceId(instance.getId());
        }
        nodeIoParamValueService.saveBatch(values);
        return Result.success();
    }
}
