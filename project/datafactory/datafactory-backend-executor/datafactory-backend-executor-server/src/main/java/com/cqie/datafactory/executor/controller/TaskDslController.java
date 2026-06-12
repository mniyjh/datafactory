package com.cqie.datafactory.executor.controller;

import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.service.TaskDslService;
import com.cqie.datafactory.executor.service.dto.TaskDslCreateDTO;
import com.cqie.datafactory.executor.service.dto.TaskDslPromoteDTO;
import com.cqie.datafactory.executor.service.vo.TaskDslVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.executor.entity.NodeInstance;
import com.cqie.datafactory.executor.entity.NodeIoParamValue;
import com.cqie.datafactory.executor.mapper.NodeInstanceMapper;
import com.cqie.datafactory.executor.mapper.NodeIoParamValueMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/task-dsl")
public class TaskDslController {

    private final TaskDslService taskDslService;
    private final NodeInstanceMapper nodeInstanceMapper;
    private final NodeIoParamValueMapper nodeIoParamValueMapper;

    public TaskDslController(TaskDslService taskDslService,
                              NodeInstanceMapper nodeInstanceMapper,
                              NodeIoParamValueMapper nodeIoParamValueMapper) {
        this.taskDslService = taskDslService;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.nodeIoParamValueMapper = nodeIoParamValueMapper;
    }

    @PostMapping("/version")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Long> createVersion(@Valid @RequestBody TaskDslCreateDTO dto) {
        return Result.success(taskDslService.createVersion(dto));
    }

    @PutMapping("/version/{versionId}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> updateVersion(@PathVariable("versionId") Long versionId, @RequestBody TaskDslCreateDTO dto) {
        taskDslService.updateVersion(versionId, dto);
        return Result.success();
    }

    @PostMapping("/version/{versionId}/publish")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> publish(@PathVariable("versionId") Long versionId) {
        taskDslService.publish(versionId);
        return Result.success();
    }

    @DeleteMapping("/version/{versionId}")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> delete(@PathVariable("versionId") Long versionId) {
        taskDslService.delete(versionId);
        return Result.success();
    }

    @PostMapping("/version/{versionId}/current")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> setCurrent(@PathVariable("versionId") Long versionId) {
        taskDslService.setCurrent(versionId);
        return Result.success();
    }

    @PostMapping("/{taskId}/promote")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> promote(@PathVariable("taskId") Long taskId, @Valid @RequestBody TaskDslPromoteDTO dto) {
        taskDslService.promote(taskId, dto);
        return Result.success();
    }

    @PostMapping("/{taskId}/rollback-env")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> rollbackEnv(@PathVariable("taskId") Long taskId, @Valid @RequestBody TaskDslPromoteDTO dto) {
        taskDslService.promote(taskId, dto);
        return Result.success();
    }

    @PostMapping("/version/{id}/rollback")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> rollbackVersion(@PathVariable("id") Long versionId) {
        taskDslService.rollbackToPrev(versionId);
        return Result.success();
    }

    @GetMapping("/{taskId}/versions")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<List<TaskDslVO>> versions(@PathVariable("taskId") Long taskId, @RequestParam(value = "environment", required = false) String environment) {
        return Result.success(taskDslService.listByTaskAndEnv(taskId, environment));
    }

    @GetMapping("/{taskId}/current")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<TaskDslVO> current(@PathVariable("taskId") Long taskId, @RequestParam("environment") String environment) {
        return Result.success(taskDslService.current(taskId, environment));
    }

    @GetMapping("/{taskId}/outdatedNodes")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<String> outdatedNodes(@PathVariable("taskId") Long taskId, @RequestParam("environment") String environment) {
        return Result.success(taskDslService.outdatedNodes(taskId, environment));
    }

    @GetMapping("/{taskId}/page")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<PageResult<TaskDslVO>> page(@PathVariable("taskId") Long taskId,
                                              @RequestParam(value = "environment", required = false) String environment,
                                              @RequestParam(value = "current", defaultValue = "1") Long current,
                                              @RequestParam(value = "size", defaultValue = "10") Long size) {
        return Result.success(taskDslService.page(taskId, environment, current, size));
    }

    @PostMapping("/{taskDslId}/sync-nodes")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> syncNodes(@PathVariable("taskDslId") Long taskDslId) {
        taskDslService.setCurrent(taskDslId);
        return Result.success();
    }

    @GetMapping("/{taskDslId}/all-io-params")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<List<Map<String, Object>>> allIoParams(@PathVariable("taskDslId") Long taskDslId) {
        // 1. 查该版本下所有节点实例
        List<NodeInstance> instances = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<NodeInstance>()
                        .eq(NodeInstance::getTaskDslId, taskDslId));

        // 2. 构建 nodeInstanceId -> NodeInstance 映射
        Map<Long, NodeInstance> instanceMap = new HashMap<>();
        for (NodeInstance inst : instances) {
            instanceMap.put(inst.getId(), inst);
        }

        // 3. 收集所有 nodeInstanceId
        List<Long> instanceIds = new ArrayList<>(instanceMap.keySet());
        if (instanceIds.isEmpty()) {
            return Result.success(List.of());
        }

        // 4. 批量查询所有 IO 参数
        List<NodeIoParamValue> allParams = nodeIoParamValueMapper.selectList(
                new LambdaQueryWrapper<NodeIoParamValue>()
                        .in(NodeIoParamValue::getNodeInstanceId, instanceIds)
                        .orderByAsc(NodeIoParamValue::getSortOrder, NodeIoParamValue::getId));

        // 5. 组装返回数据，附加 nodeId 和 nodeName
        List<Map<String, Object>> result = new ArrayList<>();
        for (NodeIoParamValue param : allParams) {
            NodeInstance inst = instanceMap.get(param.getNodeInstanceId());
            if (inst == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("nodeId", inst.getNodeId());
            item.put("nodeName", inst.getNodeName());
            item.put("ioType", param.getIoType());
            item.put("paramCode", param.getParamCode());
            item.put("paramName", param.getParamName());
            item.put("dataType", param.getDataType());
            item.put("sourceType", param.getSourceType());
            item.put("sourceValue", param.getSourceValue());
            item.put("sortOrder", param.getSortOrder());
            result.add(item);
        }

        return Result.success(result);
    }
}
