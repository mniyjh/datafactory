package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.configuration.controller.dto.ScriptVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ScriptVersionVO;
import com.cqie.datafactory.configuration.entity.ScriptVersion;

import java.util.List;
import java.util.Map;

public interface ScriptVersionService extends IService<ScriptVersion> {
    List<ScriptVersionVO> listVersions(Long scriptId, String environment);
    void createVersion(ScriptVersionCreateDTO dto);
    void updateVersion(Long id, ScriptVersionCreateDTO dto);
    void deleteVersion(Long id);
    void publishVersion(Long id);
    void selectCurrent(Long id);
    void promote(Long versionId, String toEnvironment);
    Map<String, Object> testScript(Long versionId, String inputJson);
}
