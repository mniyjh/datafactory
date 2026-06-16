package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.executor.entity.TaskDslEntity;
import com.cqie.datafactory.executor.mapper.TaskDslMapper;
import com.cqie.datafactory.executor.service.TaskDslService;
import com.cqie.datafactory.executor.service.ComponentSnapshotService;
import com.cqie.datafactory.executor.service.dto.TaskDslCreateDTO;
import com.cqie.datafactory.executor.service.dto.TaskDslPromoteDTO;
import com.cqie.datafactory.executor.service.vo.TaskDslVO;
import com.cqie.datafactory.executor.engine.core.DslParser;
import com.cqie.datafactory.executor.engine.core.DslValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskDslServiceImpl extends ServiceImpl<TaskDslMapper, TaskDslEntity> implements TaskDslService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ComponentSnapshotService componentSnapshotService;

    private final DslParser dslParser = new DslParser();
    private final DslValidator dslValidator = new DslValidator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(TaskDslCreateDTO dto) {
        validateDslContent(dto.getDslContent());
        TaskDslEntity entity = new TaskDslEntity();
        entity.setTaskId(dto.getTaskId());
        entity.setEnvironment(dto.getEnvironment());
        String next = dto.getVersion() == null || dto.getVersion().isBlank()
                ? nextVersion(dto.getTaskId(), dto.getEnvironment())
                : dto.getVersion();
        entity.setVersion(next);
        entity.setDslContent(dto.getDslContent());
        entity.setChangeLog(dto.getChangeLog());
        entity.setIsCurrent(1);
        entity.setEnvStatus(1);
        entity.setPublishStatus(0);
        entity.setCreatedBy("admin");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedBy("admin");
        entity.setUpdatedTime(LocalDateTime.now());
        save(entity);
        clearCurrent(dto.getTaskId(), dto.getEnvironment(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVersion(Long versionId, TaskDslCreateDTO dto) {
        TaskDslEntity entity = getExisting(versionId);
        if (entity.getPublishStatus() != null && entity.getPublishStatus() == 1) {
            throw new BusinessException("已发布版本不可编辑");
        }
        if (dto.getDslContent() != null) {
            validateDslContent(dto.getDslContent());
            entity.setDslContent(dto.getDslContent());
        }
        if (dto.getChangeLog() != null)
            entity.setChangeLog(dto.getChangeLog());
        if (dto.getVersion() != null && !dto.getVersion().isBlank())
            entity.setVersion(dto.getVersion());
        entity.setUpdatedBy("admin");
        entity.setUpdatedTime(LocalDateTime.now());
        updateById(entity);
    }

    @Override
    public void publish(Long versionId) {
        TaskDslEntity entity = getExisting(versionId);
        entity.setPublishStatus(1);
        entity.setUpdatedBy("admin");
        entity.setUpdatedTime(LocalDateTime.now());
        updateById(entity);
    }

    @Override
    public void delete(Long versionId) {
        TaskDslEntity entity = getExisting(versionId);
        if (entity.getPublishStatus() != null && entity.getPublishStatus() == 1) {
            throw new BusinessException("已发布版本不可删除");
        }
        removeById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackToPrev(Long versionId) {
        TaskDslEntity current = getExisting(versionId);
        TaskDslEntity prev = getOne(new LambdaQueryWrapper<TaskDslEntity>()
                .eq(TaskDslEntity::getTaskId, current.getTaskId())
                .eq(TaskDslEntity::getEnvironment, current.getEnvironment())
                .lt(TaskDslEntity::getCreatedTime, current.getCreatedTime())
                .orderByDesc(TaskDslEntity::getCreatedTime)
                .last("limit 1"));
        if (prev == null) {
            throw new BusinessException("没有可回退的上一个版本");
        }
        prev.setIsCurrent(1);
        prev.setUpdatedBy("admin");
        prev.setUpdatedTime(LocalDateTime.now());
        updateById(prev);
        current.setIsCurrent(0);
        current.setUpdatedBy("admin");
        current.setUpdatedTime(LocalDateTime.now());
        updateById(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promote(Long taskId, TaskDslPromoteDTO dto) {
        TaskDslEntity source;
        if (dto.getSourceVersionId() != null) {
            source = getExisting(dto.getSourceVersionId());
        } else {
            source = getCurrentPublished(taskId, dto.getFromEnvironment());
        }

        // 校验目标环境是否已经存在该版本
        long existsCount = lambdaQuery()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(TaskDslEntity::getEnvironment, dto.getToEnvironment())
                .eq(TaskDslEntity::getVersion, source.getVersion())
                .count();
        if (existsCount > 0) {
            throw new BusinessException(
                    "目标环境 (" + dto.getToEnvironment() + ") 已存在版本 " + source.getVersion() + "，请勿重复发布");
        }

        System.out.println("DEBUG: Promote from " + dto.getFromEnvironment() + " to " + dto.getToEnvironment()
                + " for task " + taskId);
        System.out.println("DEBUG: Source DSL content length: "
                + (source.getDslContent() != null ? source.getDslContent().length() : 0));

        clearCurrent(taskId, dto.getToEnvironment(), null);
        TaskDslEntity target = new TaskDslEntity();
        target.setTaskId(taskId);
        target.setEnvironment(dto.getToEnvironment());
        target.setVersion(source.getVersion());
        target.setDslContent(source.getDslContent());
        target.setChangeLog(dto.getChangeLog());
        target.setIsCurrent(1);
        target.setEnvStatus(1);
        target.setPublishStatus(1);
        target.setCreatedBy("admin");
        target.setCreatedTime(LocalDateTime.now());
        target.setUpdatedBy("admin");
        target.setUpdatedTime(LocalDateTime.now());

        boolean saved = save(target);
        System.out.println("DEBUG: Promote target saved: " + saved + ", id: " + target.getId());
        // 重建目标环境的节点快照
        componentSnapshotService.rebuildNodeSnapshotByTaskDsl(target.getId());
    }

    @Override
    public List<TaskDslVO> listByTaskAndEnv(Long taskId, String environment) {
        return lambdaQuery()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(environment != null && !environment.isBlank(), TaskDslEntity::getEnvironment, environment)
                .orderByDesc(TaskDslEntity::getCreatedTime)
                .list().stream().map(this::toVO).toList();
    }

    @Override
    public TaskDslVO current(Long taskId, String environment) {
        TaskDslEntity entity = getOne(new LambdaQueryWrapper<TaskDslEntity>()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(TaskDslEntity::getEnvironment, environment)
                .eq(TaskDslEntity::getIsCurrent, 1)
                .orderByDesc(TaskDslEntity::getCreatedTime)
                .last("limit 1"));
        return entity == null ? null : toVO(entity);
    }

    @Override
    public List<String> outdatedNodes(Long taskId, String environment) {
        // 根据DSL版本ID查询node_instance表中sync_status=1的节点
        String sql = """
            SELECT DISTINCT ni.node_id FROM node_instance ni
            INNER JOIN task_dsl td ON td.id = ni.task_dsl_id
            WHERE td.task_id = ? AND td.environment = ? AND ni.sync_status = 1
            """;
        try {
            return jdbcTemplate.queryForList(sql, String.class, taskId, environment);
        } catch (Exception e) {
            throw new BusinessException("检测过时节点失败");
        }
    }

    @Override
    public PageResult<TaskDslVO> page(Long taskId, String environment, long current, long size) {
        Page<TaskDslEntity> page = this.page(new Page<>(current, size), new LambdaQueryWrapper<TaskDslEntity>()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(environment != null && !environment.isBlank(), TaskDslEntity::getEnvironment, environment)
                .orderByDesc(TaskDslEntity::getCreatedTime));
        List<TaskDslVO> records = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrent(Long versionId) {
        TaskDslEntity entity = getExisting(versionId);
        clearCurrent(entity.getTaskId(), entity.getEnvironment(), null);
        entity.setIsCurrent(1);
        entity.setUpdatedBy("admin");
        entity.setUpdatedTime(LocalDateTime.now());
        updateById(entity);
    }

    private void clearCurrent(Long taskId, String environment, Long keepId) {
        lambdaUpdate().eq(TaskDslEntity::getTaskId, taskId)
                .eq(TaskDslEntity::getEnvironment, environment)
                .ne(keepId != null, TaskDslEntity::getId, keepId)
                .set(TaskDslEntity::getIsCurrent, 0)
                .update();
    }

    private void validateDslContent(String dslContent) {
        dslValidator.validate(dslParser.parse(dslContent));
    }

    private TaskDslEntity getCurrentPublished(Long taskId, String environment) {
        TaskDslEntity entity = getOne(new LambdaQueryWrapper<TaskDslEntity>()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(TaskDslEntity::getEnvironment, environment)
                .eq(TaskDslEntity::getIsCurrent, 1)
                .eq(TaskDslEntity::getPublishStatus, 1)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("当前环境没有已发布版本");
        }
        return entity;
    }

    private TaskDslEntity getExisting(Long id) {
        TaskDslEntity entity = getById(id);
        if (entity == null) {
            throw new BusinessException("版本不存在");
        }
        return entity;
    }

    private String nextVersion(Long taskId, String environment) {
        TaskDslEntity current = getOne(new LambdaQueryWrapper<TaskDslEntity>()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(TaskDslEntity::getEnvironment, environment)
                .orderByDesc(TaskDslEntity::getCreatedTime)
                .last("limit 1"));
        if (current == null || current.getVersion() == null)
            return "v1.0.0";
        String v = current.getVersion().replace("v", "");
        String[] parts = v.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        patch++;
        if (patch > 9) {
            patch = 0;
            minor++;
        }
        if (minor > 9) {
            minor = 0;
            major++;
        }
        return "v" + major + "." + minor + "." + patch;
    }

    private TaskDslVO toVO(TaskDslEntity entity) {
        TaskDslVO vo = new TaskDslVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setVersion(entity.getVersion());
        vo.setEnvironment(entity.getEnvironment());
        vo.setDslContent(entity.getDslContent());
        vo.setChangeLog(entity.getChangeLog());
        vo.setIsCurrent(entity.getIsCurrent());
        vo.setEnvStatus(entity.getEnvStatus());
        vo.setPublishStatus(entity.getPublishStatus());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedTime(entity.getCreatedTime());
        vo.setUpdatedTime(entity.getUpdatedTime());
        return vo;
    }
}
