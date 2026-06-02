package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.ExternalApiFeignClient;
import com.cqie.datafactory.executor.feign.vo.ApiVersionResolveVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class ApiPlugin implements ComponentPlugin {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExternalApiFeignClient externalApiFeignClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiPlugin(ExternalApiFeignClient externalApiFeignClient) {
        this.externalApiFeignClient = externalApiFeignClient;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("API", "HTTP", "REST"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String url = readFieldValue(fieldValues, "url", "URL", "apiUrl", "endpoint");
        String method = readFieldValue(fieldValues, "method", "httpMethod");
        if (method.isBlank()) method = "POST";

        if (!url.isBlank()) {
            return doHttpCall(url, method, context.getResolvedInputs(), null);
        }

        String apiCode = readFieldValue(fieldValues, "apiCode", "api_code");
        if (!apiCode.isBlank()) {
            return executeByApiCode(apiCode, method, context);
        }

        String apiId = readFieldValue(fieldValues, "apiId", "externalApiId");
        if (!apiId.isBlank()) {
            return executeByApiId(Long.parseLong(apiId), method, context);
        }

        throw new BusinessException("API组件缺少url、apiCode或apiId配置");
    }

    private Map<String, Object> executeByApiCode(String apiCode, String method, PluginContext context) {
        Result<ApiVersionResolveVO> result = externalApiFeignClient.resolveApiByCode(
                apiCode, context.getEnvironment());
        if (result.getCode() != 0 || result.getData() == null) {
            throw new BusinessException("API查询失败: " + result.getMessage());
        }
        ApiVersionResolveVO api = result.getData();
        return buildApiResult(api, method, context);
    }

    private Map<String, Object> executeByApiId(Long apiId, String method, PluginContext context) {
        Result<ApiVersionResolveVO> result = externalApiFeignClient.resolveApiVersion(
                apiId, context.getEnvironment());
        if (result.getCode() != 0 || result.getData() == null) {
            throw new BusinessException("API版本查询失败: " + result.getMessage());
        }
        return buildApiResult(result.getData(), method, context);
    }

    private Map<String, Object> buildApiResult(ApiVersionResolveVO api, String method, PluginContext context) {
        Map<String, Object> headers = new HashMap<>();
        if (api.getRequestHeaders() != null && !api.getRequestHeaders().isBlank()) {
            try {
                JsonNode headerNode = objectMapper.readTree(api.getRequestHeaders());
                if (headerNode.isObject()) {
                    headerNode.fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText("")));
                }
            } catch (Exception ignore) {}
        }

        Map<String, Object> requestBody = new HashMap<>(context.getResolvedInputs());
        if (api.getRequestBody() != null && !api.getRequestBody().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> templateBody = objectMapper.readValue(api.getRequestBody(), Map.class);
                templateBody.putAll(requestBody);
                requestBody = templateBody;
            } catch (Exception ignore) {}
        }

        String httpMethod = api.getRequestMethod() != null && !api.getRequestMethod().isBlank()
                ? api.getRequestMethod() : method;
        return doHttpCall(api.getRequestUrl(), httpMethod, requestBody, headers);
    }

    private Map<String, Object> doHttpCall(String url, String method, Map<String, Object> requestBody,
                                           Map<String, Object> customHeaders) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (customHeaders != null) {
            customHeaders.forEach((k, v) -> httpHeaders.set(k, Objects.toString(v, "")));
        }
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                requestBody != null ? requestBody : new HashMap<>(), httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, request, String.class);

        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", response.getStatusCode().value());
        result.put("body", response.getBody());
        return result;
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) return val.asText();
        }
        return "";
    }
}
