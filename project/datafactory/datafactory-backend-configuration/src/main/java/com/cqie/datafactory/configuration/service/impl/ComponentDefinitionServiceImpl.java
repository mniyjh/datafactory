package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ComponentDefinitionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ComponentDefinitionVO;
import com.cqie.datafactory.configuration.entity.ComponentDefinition;
import com.cqie.datafactory.configuration.mapper.ComponentDefinitionMapper;
import com.cqie.datafactory.configuration.service.ComponentDefinitionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Service
public class ComponentDefinitionServiceImpl extends ServiceImpl<ComponentDefinitionMapper, ComponentDefinition> implements ComponentDefinitionService {
    @Override
    public PageResult<ComponentDefinitionVO> pageDefinition(PageQuery pageQuery, String keyword) {
        Page<ComponentDefinition> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        LambdaQueryWrapper<ComponentDefinition> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ComponentDefinition::getComponentCode, keyword)
                    .or().like(ComponentDefinition::getComponentName, keyword)
                    .or().like(ComponentDefinition::getComponentType, keyword));
        }
        wrapper.orderByDesc(ComponentDefinition::getIsSystem, ComponentDefinition::getCreatedTime);
        Page<ComponentDefinition> result = this.page(page, wrapper);
        PageResult<ComponentDefinitionVO> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return pageResult;
    }

    @Override
    public void createDefinition(ComponentDefinitionCreateDTO dto) {
        ComponentDefinition entity = new ComponentDefinition();
        copy(entity, dto);
        this.save(entity);
    }

    @Override
    public void updateDefinition(Long id, ComponentDefinitionCreateDTO dto) {
        ComponentDefinition entity = this.getById(id);
        if (entity == null || Integer.valueOf(1).equals(entity.getIsSystem())) {
            return;
        }
        copy(entity, dto);
        this.updateById(entity);
    }

    @Override
    public void deleteDefinition(Long id) {
        ComponentDefinition entity = this.getById(id);
        if (entity != null && !Integer.valueOf(1).equals(entity.getIsSystem())) {
            this.removeById(id);
        }
    }

    @Override
    public void toggleStatus(Long id) {
        ComponentDefinition entity = this.getById(id);
        if (entity == null) return;
        entity.setStatus(entity.getStatus() != null && entity.getStatus() == 1 ? 0 : 1);
        this.updateById(entity);
    }

    private void copy(ComponentDefinition entity, ComponentDefinitionCreateDTO dto) {
        entity.setComponentCode(dto.getComponentCode());
        entity.setComponentName(dto.getComponentName());
        entity.setComponentType(dto.getComponentType());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setIsSystem(dto.getIsSystem() == null ? 0 : dto.getIsSystem());
        entity.setDefaultConfig(dto.getDefaultConfig());
        entity.setDescription(dto.getDescription());
    }

    private ComponentDefinitionVO toVO(ComponentDefinition entity) {
        ComponentDefinitionVO vo = new ComponentDefinitionVO();
        vo.setId(entity.getId());
        vo.setComponentCode(entity.getComponentCode());
        vo.setComponentName(entity.getComponentName());
        vo.setComponentType(entity.getComponentType());
        vo.setStatus(entity.getStatus());
        vo.setIsSystem(entity.getIsSystem());
        vo.setDefaultConfig(entity.getDefaultConfig());
        vo.setDescription(entity.getDescription());
        vo.setCreatedTime(entity.getCreatedTime());
        return vo;
    }
}
