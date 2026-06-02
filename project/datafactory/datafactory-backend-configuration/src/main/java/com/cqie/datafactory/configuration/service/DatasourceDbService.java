package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbTestDTO;
import com.cqie.datafactory.configuration.controller.vo.DatasourceDbVO;
import com.cqie.datafactory.configuration.entity.DatasourceDb;

public interface DatasourceDbService extends IService<DatasourceDb> {
    PageResult<DatasourceDbVO> pageDb(PageQuery pageQuery, String keyword);
    void createDb(DatasourceDbCreateDTO dto);
    void updateDb(Long id, DatasourceDbCreateDTO dto);
    void deleteDb(Long id);
    void testConnection(DatasourceDbTestDTO dto);
}