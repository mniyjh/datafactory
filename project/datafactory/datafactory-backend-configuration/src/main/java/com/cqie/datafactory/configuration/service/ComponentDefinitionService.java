package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ComponentDefinitionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ComponentDefinitionVO;
import com.cqie.datafactory.configuration.entity.ComponentDefinition;

public interface ComponentDefinitionService extends IService<ComponentDefinition> {
    PageResult<ComponentDefinitionVO> pageDefinition(PageQuery pageQuery, String keyword);

    void createDefinition(ComponentDefinitionCreateDTO dto);

    void updateDefinition(Long id, ComponentDefinitionCreateDTO dto);

    void deleteDefinition(Long id);

    void toggleStatus(Long id);
}
