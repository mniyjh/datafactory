package com.cqie.datafactory.configuration.controller.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.entity.ExternalApi;
import com.cqie.datafactory.configuration.entity.ExternalApiVersion;
import com.cqie.datafactory.configuration.service.ExternalApiService;
import com.cqie.datafactory.configuration.service.ExternalApiVersionService;
import com.cqie.datafactory.executor.feign.vo.ApiVersionResolveVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/external-api")
public class ExternalApiFeignController {

    private final ExternalApiService externalApiService;
    private final ExternalApiVersionService externalApiVersionService;

    public ExternalApiFeignController(ExternalApiService externalApiService, ExternalApiVersionService externalApiVersionService) {
        this.externalApiService = externalApiService;
        this.externalApiVersionService = externalApiVersionService;
    }

    @GetMapping("/version/resolve")
    public Result<ApiVersionResolveVO> resolveApiVersion(@RequestParam("apiId") Long apiId, @RequestParam("environment") String environment) {
        ExternalApi api = externalApiService.getOne(new LambdaQueryWrapper<ExternalApi>()
                .eq(ExternalApi::getId, apiId));

        if (api == null) {
            return Result.fail("API不存在");
        }

        ExternalApiVersion version = externalApiVersionService.getOne(new LambdaQueryWrapper<ExternalApiVersion>()
                .eq(ExternalApiVersion::getApiId, apiId)
                .eq(ExternalApiVersion::getIsCurrent, 1)
                .orderByDesc(ExternalApiVersion::getUpdatedTime)
                .last("limit 1"));

        if (version == null) {
            return Result.fail("未找到可用API版本");
        }

        ApiVersionResolveVO vo = new ApiVersionResolveVO();
        vo.setId(version.getId());
        vo.setApiId(apiId);
        vo.setEnvironment(version.getEnvironment());
        vo.setRequestMethod(version.getRequestMethod());
        vo.setRequestUrl(version.getRequestUrl());
        vo.setRequestHeaders(version.getRequestHeaders());
        vo.setRequestBody(version.getRequestBody());
        vo.setContentType(version.getContentType());
        vo.setQueryParams(version.getQueryParams());
        vo.setAuthType(version.getAuthType());
        vo.setAuthConfig(version.getAuthConfig());
        vo.setTimeout(version.getTimeout());
        vo.setRetryCount(version.getRetryCount());
        return Result.success(vo);
    }

    @GetMapping("/version/resolve-by-code")
    public Result<ApiVersionResolveVO> resolveByCode(@RequestParam("apiCode") String apiCode, @RequestParam("environment") String environment) {
        ExternalApi api = externalApiService.getOne(new LambdaQueryWrapper<ExternalApi>()
                .eq(ExternalApi::getApiCode, apiCode));

        if (api == null) {
            return Result.fail("API不存在: " + apiCode);
        }

        ExternalApiVersion version = externalApiVersionService.getOne(new LambdaQueryWrapper<ExternalApiVersion>()
                .eq(ExternalApiVersion::getApiId, api.getId())
                .eq(ExternalApiVersion::getIsCurrent, 1)
                .orderByDesc(ExternalApiVersion::getUpdatedTime)
                .last("limit 1"));

        if (version == null) {
            return Result.fail("未找到可用API版本");
        }

        ApiVersionResolveVO vo = new ApiVersionResolveVO();
        vo.setId(version.getId());
        vo.setApiId(api.getId());
        vo.setEnvironment(version.getEnvironment());
        vo.setRequestMethod(version.getRequestMethod());
        vo.setRequestUrl(version.getRequestUrl());
        vo.setRequestHeaders(version.getRequestHeaders());
        vo.setRequestBody(version.getRequestBody());
        vo.setContentType(version.getContentType());
        vo.setQueryParams(version.getQueryParams());
        vo.setAuthType(version.getAuthType());
        vo.setAuthConfig(version.getAuthConfig());
        vo.setTimeout(version.getTimeout());
        vo.setRetryCount(version.getRetryCount());
        return Result.success(vo);
    }
}
