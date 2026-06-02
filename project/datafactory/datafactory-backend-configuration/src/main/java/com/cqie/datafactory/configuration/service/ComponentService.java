package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ComponentCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.ComponentFieldSaveDTO;
import com.cqie.datafactory.configuration.controller.vo.ComponentMetaVO;
import com.cqie.datafactory.configuration.controller.vo.ComponentVO;
import com.cqie.datafactory.configuration.entity.Component;

import java.util.List;
import java.util.Map;

public interface ComponentService extends IService<Component> {
    PageResult<ComponentVO> pageComponent(PageQuery pageQuery, String keyword);

    Long createComponent(ComponentCreateDTO dto);

    void updateComponent(Long id, ComponentCreateDTO dto);

    void deleteComponent(Long id);

    void toggleStatus(Long id);

    ComponentMetaVO getComponentMeta(Long componentId);

    void saveComponentFields(Long componentId, List<ComponentFieldSaveDTO> fields);

    void syncToTask(Long componentId, Long taskId);

    String getComponentFieldVersions(Long componentId);
    Map<String, Object> testComponent(Long componentId);
    int syncComponentFieldsToNodes(Long componentId);
}
