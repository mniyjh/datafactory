package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.vo.ApiVersionResolveVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "datafactory-backend-configuration", contextId = "externalApiFeignClient", path = "/feign/external-api")
public interface ExternalApiFeignClient {
    @GetMapping("/version/resolve")
    Result<ApiVersionResolveVO> resolveApiVersion(@RequestParam("apiId") Long apiId, @RequestParam("environment") String environment);

    @GetMapping("/version/resolve-by-code")
    Result<ApiVersionResolveVO> resolveApiByCode(@RequestParam("apiCode") String apiCode, @RequestParam("environment") String environment);
}
