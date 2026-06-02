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
    private Integer timeout;

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
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
}
