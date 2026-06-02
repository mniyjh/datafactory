package com.cqie.datafactory.configuration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.OpenApiCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.OpenApiVO;
import com.cqie.datafactory.configuration.entity.OpenApi;

import java.util.Map;

public interface OpenApiService extends IService<OpenApi> {
    PageResult<OpenApiVO> pageApi(PageQuery pageQuery, String keyword);
    void createApi(OpenApiCreateDTO dto);
    void updateApi(Long id, OpenApiCreateDTO dto);
    void deleteApi(Long id);
    void toggleStatus(Long id);
    void generateKey(Long id);
    Map<String, Object> invokeByCode(String code, String appSecret, Map<String, Object> payload);
    Map<String, Object> invokeSync(String code, String appSecret, Map<String, Object> payload, long timeoutMs);
    Map<String, Object> queryResult(String executionId);
}
