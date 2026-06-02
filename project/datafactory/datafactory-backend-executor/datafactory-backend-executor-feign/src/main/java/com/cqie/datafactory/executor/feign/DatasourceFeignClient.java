package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.vo.DbVersionResolveVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "datafactory-backend-configuration", contextId = "datasourceFeignClient", path = "/feign/datasource")
public interface DatasourceFeignClient {
    @GetMapping("/db-version/resolve")
    Result<DbVersionResolveVO> resolveDbVersion(@RequestParam("dbId") Long dbId, @RequestParam("environment") String environment);
}
