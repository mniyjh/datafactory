package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.controller.dto.ComponentCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.ComponentFieldSaveDTO;
import com.cqie.datafactory.configuration.controller.vo.ComponentFieldVO;
import com.cqie.datafactory.configuration.controller.vo.ComponentMetaVO;
import com.cqie.datafactory.configuration.controller.vo.ComponentVO;
import com.cqie.datafactory.configuration.entity.Component;
import com.cqie.datafactory.configuration.entity.ComponentField;
import com.cqie.datafactory.configuration.entity.ComponentFieldHistory;
import com.cqie.datafactory.configuration.entity.ComponentIoParam;
import com.cqie.datafactory.configuration.entity.ComponentIoParamHistory;
import com.cqie.datafactory.configuration.mapper.ComponentFieldHistoryMapper;
import com.cqie.datafactory.configuration.mapper.ComponentIoParamHistoryMapper;
import com.cqie.datafactory.configuration.mapper.ComponentFieldMapper;
import com.cqie.datafactory.configuration.mapper.ComponentMapper;
import com.cqie.datafactory.configuration.service.ComponentService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Set.of;

@Service
public class ComponentServiceImpl extends ServiceImpl<ComponentMapper, Component> implements ComponentService {

    private static final Set<String> ALLOWED_COMPONENT_TYPES = of("数据接入", "数据处理", "流程控制");

    @Autowired
    private ComponentFieldMapper componentFieldMapper;

    @Autowired
    private ComponentFieldHistoryMapper componentFieldHistoryMapper;

    @Autowired
    private ComponentIoParamHistoryMapper componentIoParamHistoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<ComponentVO> pageComponent(PageQuery pageQuery, String keyword) {
        Page<Component> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        LambdaQueryWrapper<Component> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Component::getComponentCode, keyword)
                    .or().like(Component::getComponentName, keyword));
        }
        wrapper.orderByDesc(Component::getCreatedTime);
        Page<Component> result = this.page(page, wrapper);

        PageResult<ComponentVO> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return pageResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComponent(ComponentCreateDTO dto) {
        Component component = new Component();
        component.setComponentCode(dto.getCode());
        component.setComponentName(dto.getName());
        component.setComponentType(normalizeAndValidateType(dto.getType()));
        component.setCategory(dto.getCategory());
        component.setVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0.0");
        component.setDescription(dto.getDesc());
        component.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
        try {
            this.save(component);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("组件编码已存在：" + dto.getCode());
        }
        return component.getId();
    }

    @Override
    public void updateComponent(Long id, ComponentCreateDTO dto) {
        Component component = this.getById(id);
        if (component == null) {
            return;
        }
        component.setComponentName(dto.getName());
        component.setComponentType(normalizeAndValidateType(dto.getType()));
        component.setCategory(dto.getCategory());
        component.setVersion(dto.getVersion());
        component.setDescription(dto.getDesc());
        component.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
        this.updateById(component);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComponent(Long id) {
        Component component = this.getById(id);
        if (component == null) {
            return;
        }

        long taskRefCount = this.baseMapper.countTaskDslReferenceByComponentId(id);
        if (taskRefCount > 0) {
            throw new BusinessException("当前组件已关联任务，无法删除");
        }

        LambdaQueryWrapper<ComponentField> fieldDeleteWrapper = new LambdaQueryWrapper<>();
        fieldDeleteWrapper.eq(ComponentField::getComponentId, id);
        componentFieldMapper.delete(fieldDeleteWrapper);

        boolean removed = this.removeById(id);
        if (!removed) {
            throw new BusinessException("删除组件失败，记录不存在或已被删除");
        }
    }

    @Override
    public void toggleStatus(Long id) {
        Component component = this.getById(id);
        if (component == null) {
            return;
        }
        component.setStatus(component.getStatus() != null && component.getStatus() == 1 ? 0 : 1);
        this.updateById(component);
    }

    @Override
    public ComponentMetaVO getComponentMeta(Long componentId) {
        Component component = this.getById(componentId);
        if (component == null) {
            return null;
        }
        ComponentMetaVO metaVO = new ComponentMetaVO();
        metaVO.setComponentId(component.getId());
        metaVO.setComponentCode(component.getComponentCode());
        metaVO.setComponentName(component.getComponentName());

        LambdaQueryWrapper<ComponentField> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(ComponentField::getComponentId, componentId).orderByAsc(ComponentField::getSortOrder, ComponentField::getId);
        List<ComponentFieldVO> fields = componentFieldMapper.selectList(fieldWrapper).stream().map(item -> {
            ComponentFieldVO vo = new ComponentFieldVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        metaVO.setFields(fields);
        metaVO.setInputParams(new ArrayList<>());
        metaVO.setOutputParams(new ArrayList<>());
        return metaVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveComponentFields(Long componentId, List<ComponentFieldSaveDTO> fields) {
        // 先校验所有字段，校验通过后再操作数据，防止先删后验导致数据丢失
        Set<String> codeSet = new HashSet<>();
        Set<String> allowedValueTypes = Set.of("STRING", "NUMBER", "BOOLEAN", "DATE", "JSON");
        Set<String> allowedWidgetTypes = Set.of("TEXTAREA", "SWITCH", "MULTI_SELECT", "DATE_PICKER");

        if (fields != null) {
            for (ComponentFieldSaveDTO dto : fields) {
                if (!StringUtils.hasText(dto.getFieldCode())) {
                    throw new BusinessException("字段编码不能为空");
                }
                if (!StringUtils.hasText(dto.getFieldName())) {
                    throw new BusinessException("字段名称不能为空");
                }
                String valueType = StringUtils.hasText(dto.getValueType()) ? dto.getValueType().toUpperCase() : "STRING";
                String widgetType = StringUtils.hasText(dto.getWidgetType()) ? dto.getWidgetType().toUpperCase() : "INPUT";
                if (!allowedValueTypes.contains(valueType)) {
                    throw new BusinessException("不支持的数据类型: " + valueType);
                }
                if (!allowedWidgetTypes.contains(widgetType)) {
                    throw new BusinessException("不支持的控件类型: " + widgetType);
                }
                if (!codeSet.add(dto.getFieldCode())) {
                    throw new BusinessException("字段编码重复: " + dto.getFieldCode());
                }
            }
        }

        // 校验通过后再删除旧字段
        LambdaQueryWrapper<ComponentField> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ComponentField::getComponentId, componentId);
        componentFieldMapper.delete(deleteWrapper);

        // 标记所有引用该组件的画布节点为过时状态
        jdbcTemplate.update(
            "UPDATE node_instance SET sync_status = 1, updated_time = NOW() WHERE component_id = ?",
            componentId
        );

        if (fields == null || fields.isEmpty()) {
            return;
        }

        int idx = 1;
        for (ComponentFieldSaveDTO dto : fields) {
            ComponentField entity = new ComponentField();
            entity.setComponentId(componentId);
            entity.setFieldCode(dto.getFieldCode());
            entity.setFieldName(dto.getFieldName());
            entity.setValueType(dto.getValueType() != null ? dto.getValueType().toUpperCase() : "STRING");
            entity.setWidgetType(dto.getWidgetType() != null ? dto.getWidgetType().toUpperCase() : "INPUT");
            entity.setWidgetProps(dto.getWidgetProps());
            entity.setDefaultValue(dto.getDefaultValue());
            entity.setRequiredFlag(dto.getRequiredFlag() == null ? 0 : dto.getRequiredFlag());
            entity.setSortOrder(dto.getSortOrder() == null ? idx : dto.getSortOrder());
            entity.setDescription(dto.getDescription());
            componentFieldMapper.insert(entity);
            idx++;
        }

        // 递增组件版本并记录变更历史
        Component component = this.getById(componentId);
        if (component != null) {
            String newVersion = bumpVersion(component.getVersion());
            component.setVersion(newVersion);
            this.updateById(component);

            ComponentFieldHistory history = new ComponentFieldHistory();
            history.setComponentId(componentId);
            history.setVersion(newVersion);
            history.setChangeType("UPDATE");
            history.setFieldSnapshot(toJson(fields));
            history.setChangeLog("字段配置变更");
            history.setCreatedBy("admin");
            history.setCreatedTime(LocalDateTime.now());
            componentFieldHistoryMapper.insert(history);

            // Record IO param snapshot alongside field history
            List<ComponentIoParam> ioParams = jdbcTemplate.query(
                "SELECT io_type, param_code, param_name, data_type, source_type, source_value, default_value, required_flag, param_space, sort_order FROM component_io_param WHERE component_id = ? ORDER BY sort_order",
                (rs, rowNum) -> {
                    ComponentIoParam p = new ComponentIoParam();
                    p.setIoType(rs.getString("io_type"));
                    p.setParamCode(rs.getString("param_code"));
                    p.setParamName(rs.getString("param_name"));
                    p.setDataType(rs.getString("data_type"));
                    p.setSourceType(rs.getString("source_type"));
                    p.setSourceValue(rs.getString("source_value"));
                    p.setDefaultValue(rs.getString("default_value"));
                    p.setRequiredFlag(rs.getInt("required_flag"));
                    p.setParamSpace(rs.getString("param_space"));
                    p.setSortOrder(rs.getInt("sort_order"));
                    return p;
                },
                componentId
            );

            ComponentIoParamHistory ioHistory = new ComponentIoParamHistory();
            ioHistory.setComponentId(componentId);
            ioHistory.setVersion(newVersion);
            ioHistory.setChangeType("UPDATE");
            ioHistory.setParamSnapshot(toJson(ioParams));
            ioHistory.setChangeLog("字段配置变更同步IO参数快照");
            ioHistory.setCreatedBy("admin");
            ioHistory.setCreatedTime(LocalDateTime.now());
            componentIoParamHistoryMapper.insert(ioHistory);
        }
    }

    private ComponentVO toVO(Component entity) {
        ComponentVO vo = new ComponentVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getComponentCode());
        vo.setName(entity.getComponentName());
        vo.setType(entity.getComponentType());
        vo.setCategory(entity.getCategory());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getStatus() != null && entity.getStatus() == 1 ? "启用" : "禁用");
        vo.setDesc(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedTime());
        return vo;
    }

    @Override
    public void syncToTask(Long componentId, Long taskId) {
        Component component = this.getById(componentId);
        if (component == null) {
            throw new BusinessException("组件不存在");
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_dsl WHERE task_id = ? AND publish_status = 0",
                Long.class,
                taskId
        );
        if (count == null || count <= 0) {
            throw new BusinessException("未找到可同步的未发布任务版本");
        }
    }

    @Override
    public String getComponentFieldVersions(Long componentId) {
        List<ComponentField> fields = componentFieldMapper.selectList(new LambdaQueryWrapper<ComponentField>()
                .eq(ComponentField::getComponentId, componentId)
                .orderByAsc(ComponentField::getSortOrder, ComponentField::getId));
        List<String> versions = fields.stream().map(f -> f.getFieldCode() + ":" + f.getValueType() + ":" + f.getWidgetType()).toList();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(versions);
        } catch (Exception e) {
            throw new BusinessException("查询字段版本失败");
        }
    }

    private String bumpVersion(String currentVersion) {
        String v = (currentVersion == null || currentVersion.isBlank()) ? "1.0.0" : currentVersion;
        String[] parts = v.split("\\.");
        int patch = Integer.parseInt(parts[2]) + 1;
        int minor = Integer.parseInt(parts[1]);
        int major = Integer.parseInt(parts[0]);
        if (patch > 99) { patch = 0; minor++; }
        if (minor > 99) { minor = 0; major++; }
        return major + "." + minor + "." + patch;
    }

    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public int syncComponentFieldsToNodes(Long componentId) {
        List<ComponentField> currentFields = componentFieldMapper.selectList(
                new LambdaQueryWrapper<ComponentField>()
                        .eq(ComponentField::getComponentId, componentId));
        Set<String> currentFieldCodes = currentFields.stream().map(ComponentField::getFieldCode).collect(Collectors.toSet());

        List<Integer> affected = new ArrayList<>();
        // 查所有标记为待同步的 node_instance
        List<Map<String, Object>> outdatedNodes = jdbcTemplate.queryForList(
                "SELECT id FROM node_instance WHERE component_id = ? AND sync_status = 1", componentId);
        for (Map<String, Object> row : outdatedNodes) {
            Long nodeInstanceId = ((Number) row.get("id")).longValue();
            // 获取已有字段值
            List<Map<String, Object>> existingValues = jdbcTemplate.queryForList(
                    "SELECT field_code FROM node_field_value WHERE node_instance_id = ?",
                    nodeInstanceId);
            Set<String> existingFieldCodes = existingValues.stream()
                    .map(m -> (String) m.get("field_code")).collect(Collectors.toSet());

            // 标记已删除的字段
            for (String existCode : existingFieldCodes) {
                if (!currentFieldCodes.contains(existCode)) {
                    jdbcTemplate.update(
                            "UPDATE node_field_value SET deprecated_flag = 1, updated_time = NOW() WHERE node_instance_id = ? AND field_code = ?",
                            nodeInstanceId, existCode);
                }
            }
            // 添加新增字段
            int sortOrder = existingFieldCodes.size() + 1;
            for (ComponentField field : currentFields) {
                if (!existingFieldCodes.contains(field.getFieldCode())) {
                    jdbcTemplate.update(
                            "INSERT INTO node_field_value (node_instance_id, field_code, field_name, value_type, widget_type, widget_props, default_value, field_value, required_flag, sort_order, description, field_snapshot, deprecated_flag, created_by, created_time, updated_by, updated_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0,'admin',NOW(),'admin',NOW())",
                            nodeInstanceId, field.getFieldCode(), field.getFieldName(), field.getValueType(),
                            field.getWidgetType(), field.getWidgetProps(), field.getDefaultValue(),
                            field.getDefaultValue(), field.getRequiredFlag(), sortOrder++,
                            field.getDescription(), toJson(field));
                }
            }
            // 清除同步标记
            jdbcTemplate.update("UPDATE node_instance SET sync_status = 0, updated_time = NOW() WHERE id = ?", nodeInstanceId);
            affected.add(1);
        }
        return affected.size();
    }

    @Override
    public Map<String, Object> testComponent(Long componentId) {
        Component component = this.getById(componentId);
        if (component == null) {
            throw new BusinessException("组件不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("componentId", component.getId());
        result.put("componentCode", component.getComponentCode());
        result.put("componentName", component.getComponentName());
        result.put("componentType", component.getComponentType());
        result.put("version", component.getVersion());
        result.put("status", component.getStatus() == 1 ? "启用" : "禁用");

        List<ComponentField> fields = componentFieldMapper.selectList(
                new LambdaQueryWrapper<ComponentField>()
                        .eq(ComponentField::getComponentId, componentId)
                        .orderByAsc(ComponentField::getSortOrder));
        result.put("fieldCount", fields.size());
        result.put("fields", fields.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("code", f.getFieldCode());
            m.put("name", f.getFieldName());
            m.put("type", f.getValueType());
            m.put("required", f.getRequiredFlag() == 1);
            return m;
        }).collect(Collectors.toList()));

        if (component.getStatus() != 1) {
            result.put("testResult", "SKIP");
            result.put("message", "组件已禁用，跳过测试");
        } else if (fields.isEmpty()) {
            result.put("testResult", "WARN");
            result.put("message", "组件未定义字段");
        } else {
            result.put("testResult", "OK");
            result.put("message", "组件可用");
        }
        return result;
    }

    private String normalizeAndValidateType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            throw new BusinessException("组件分类不能为空");
        }
        String normalizedType = rawType.trim().toUpperCase();
        if (!ALLOWED_COMPONENT_TYPES.contains(normalizedType)) {
            throw new BusinessException("不支持的组件分类: " + rawType);
        }
        return normalizedType;
    }
}
