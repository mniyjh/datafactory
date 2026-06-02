package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiVersionPromoteDTO;
import com.cqie.datafactory.configuration.controller.vo.ExternalApiVersionVO;
import com.cqie.datafactory.configuration.entity.ExternalApiVersion;

import java.util.List;
import java.util.Map;

public interface ExternalApiVersionService extends IService<ExternalApiVersion> {
    List<ExternalApiVersionVO> listVersions(Long apiId, String environment);
    void createVersion(ExternalApiVersionCreateDTO dto);
    void updateVersion(Long id, ExternalApiVersionCreateDTO dto);
    void deleteVersion(Long id);
    void publishVersion(Long id);
    void promoteVersion(ExternalApiVersionPromoteDTO dto);
    void selectCurrent(Long id);
    Map<String, Object> testConnection(ExternalApiVersionCreateDTO dto);
}
