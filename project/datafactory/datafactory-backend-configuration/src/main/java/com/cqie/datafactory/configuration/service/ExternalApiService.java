package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ExternalApiVO;
import com.cqie.datafactory.configuration.entity.ExternalApi;

public interface ExternalApiService extends IService<ExternalApi> {
    PageResult<ExternalApiVO> pageApi(PageQuery pageQuery, String keyword);
    void createApi(ExternalApiCreateDTO dto);
    void updateApi(Long id, ExternalApiCreateDTO dto);
    void deleteApi(Long id);
    void toggleStatus(Long id);
}
