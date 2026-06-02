package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.vo.ScriptExecutionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "datafactory-backend-configuration", contextId = "scriptFeignClient", path = "/feign/script")
public interface ScriptFeignClient {
    @GetMapping("/version/for-execution")
    Result<ScriptExecutionVO> resolveScriptVersion(@RequestParam("scriptId") Long scriptId, @RequestParam("environment") String environment);
}
