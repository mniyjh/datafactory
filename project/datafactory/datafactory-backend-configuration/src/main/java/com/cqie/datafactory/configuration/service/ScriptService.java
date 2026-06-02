package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ScriptCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ScriptVO;
import com.cqie.datafactory.configuration.entity.Script;

public interface ScriptService extends IService<Script> {
    PageResult<ScriptVO> pageScript(PageQuery pageQuery, String keyword);
    void createScript(ScriptCreateDTO dto);
    void updateScript(Long id, ScriptCreateDTO dto);
    void deleteScript(Long id);
    void toggleStatus(Long id);
}
