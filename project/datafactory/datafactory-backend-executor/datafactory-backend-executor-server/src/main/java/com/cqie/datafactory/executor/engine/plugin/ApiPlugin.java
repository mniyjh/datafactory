package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.ExternalApiFeignClient;
import com.cqie.datafactory.executor.feign.vo.ApiVersionResolveVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.*;

@Component
public class ApiPlugin implements ComponentPlugin {

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
            return doHttpCall(url, method, context.getResolvedInputs(), null, null, null, null, 30);
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
        return buildApiResult(result.getData(), method, context);
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
        int timeout = api.getTimeout() != null && api.getTimeout() > 0 ? api.getTimeout() : 30;

        return doHttpCall(api.getRequestUrl(), httpMethod, requestBody, headers,
                api.getContentType(), api.getQueryParams(),
                buildAuthHeaders(api.getAuthType(), api.getAuthConfig()), timeout);
    }

    /**
     * 根据认证类型和配置构建认证 Header。
     * authConfig 格式: {"username":"x","password":"y"} / {"token":"x"} / {"key":"x","value":"y"}
     */
    private Map<String, String> buildAuthHeaders(String authType, String authConfig) {
        if (authType == null || authType.isBlank() || authConfig == null || authConfig.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode cfg = objectMapper.readTree(authConfig);
            switch (authType.toUpperCase()) {
                case "BASIC" -> {
                    String user = cfg.has("username") ? cfg.get("username").asText() : "";
                    String pass = cfg.has("password") ? cfg.get("password").asText() : "";
                    String encoded = Base64.getEncoder()
                            .encodeToString((user + ":" + pass).getBytes());
                    result.put("Authorization", "Basic " + encoded);
                }
                case "BEARER" -> {
                    String token = cfg.has("token") ? cfg.get("token").asText()
                            : cfg.has("bearer") ? cfg.get("bearer").asText() : "";
                    result.put("Authorization", "Bearer " + token);
                }
                case "APIKEY", "API_KEY" -> {
                    String key = cfg.has("key") ? cfg.get("key").asText() : "X-API-Key";
                    String value = cfg.has("value") ? cfg.get("value").asText()
                            : cfg.has("apiKey") ? cfg.get("apiKey").asText() : "";
                    result.put(key, value);
                }
                default -> {} // 未知类型不注入
            }
        } catch (Exception e) {
            // 认证配置解析失败，跳过
        }
        return result;
    }

    private Map<String, Object> doHttpCall(String url, String method, Map<String, Object> requestBody,
                                           Map<String, Object> customHeaders, String contentType,
                                           String queryParams, Map<String, String> authHeaders,
                                           int timeoutSec) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));

        // 拼接 query params 到 URL
        URI uri;
        if (queryParams != null && !queryParams.isBlank()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            try {
                JsonNode qpNode = objectMapper.readTree(queryParams);
                if (qpNode.isObject()) {
                    qpNode.fields().forEachRemaining(e -> builder.queryParam(e.getKey(), e.getValue().asText("")));
                }
            } catch (Exception e) {
                // queryParams 解析失败，使用原始 URL
            }
            uri = builder.build(true).toUri();
        } else {
            uri = URI.create(url);
        }

        // 构建 headers
        HttpHeaders httpHeaders = new HttpHeaders();
        String ct = (contentType != null && !contentType.isBlank()) ? contentType : "application/json";
        httpHeaders.setContentType(MediaType.parseMediaType(ct));

        if (customHeaders != null) {
            customHeaders.forEach((k, v) -> httpHeaders.set(k, Objects.toString(v, "")));
        }
        if (authHeaders != null) {
            authHeaders.forEach(httpHeaders::set);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                requestBody != null ? requestBody : new HashMap<>(), httpHeaders);

        // 构建带超时的 RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSec, 10)));  // 连接超时最多10秒
        factory.setReadTimeout(Duration.ofSeconds(timeoutSec));
        RestTemplate rt = new RestTemplate(factory);

        ResponseEntity<String> response = rt.exchange(uri, httpMethod, request, String.class);

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
