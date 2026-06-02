package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbTestDTO;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.DatasourceDbVersionVO;
import com.cqie.datafactory.configuration.entity.DatasourceDbVersion;

import java.util.List;

public interface DatasourceDbVersionService extends IService<DatasourceDbVersion> {
    List<DatasourceDbVersionVO> listVersions(Long dbId, String environment);
    void createVersion(DatasourceDbVersionCreateDTO dto);
    void selectVersion(Long id);
    void promote(Long versionId, String toEnvironment);
    void deleteVersion(Long id);
    void testConnection(DatasourceDbTestDTO dto);
}