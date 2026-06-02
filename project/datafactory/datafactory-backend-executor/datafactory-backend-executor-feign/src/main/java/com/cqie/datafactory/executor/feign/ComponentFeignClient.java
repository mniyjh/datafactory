package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "datafactory-backend-configuration", contextId = "componentFeignClient", path = "/feign/component")
public interface ComponentFeignClient {
    @GetMapping("/{id}/validate")
    Result<Map<String, Object>> validateAndGet(@PathVariable Long id);
}
