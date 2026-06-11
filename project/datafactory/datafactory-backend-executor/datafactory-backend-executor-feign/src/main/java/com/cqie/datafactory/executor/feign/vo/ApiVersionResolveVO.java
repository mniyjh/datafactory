package com.cqie.datafactory.executor.feign.vo;

public class ApiVersionResolveVO {
    private Long id;
    private Long apiId;
    private String environment;
    private String requestMethod;
    private String requestUrl;
    private String requestHeaders;
    private String requestBody;
    private String contentType;
    private String queryParams;
    private String authType;
    private String authConfig;
    private Integer timeout;
    private Integer retryCount;
    private String apiType;   // REST/SOAP/GraphQL — 来自 ExternalApi

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApiId() { return apiId; }
    public void setApiId(Long apiId) { this.apiId = apiId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getQueryParams() { return queryParams; }
    public void setQueryParams(String queryParams) { this.queryParams = queryParams; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthConfig() { return authConfig; }
    public void setAuthConfig(String authConfig) { this.authConfig = authConfig; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getApiType() { return apiType; }
    public void setApiType(String apiType) { this.apiType = apiType; }
}
