package com.cqie.datafactory.configuration.controller.dto;

public class FieldOptionsResolveDTO {
    private String sourceType;   // DB_QUERY / API_CALL
    private Long dbId;
    private Long dbVersionId;
    private Long apiId;
    private Long apiVersionId;
    private Long scriptId;
    private Long scriptVersionId;
    private String query;
    private String environment;

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }
    public Long getDbVersionId() { return dbVersionId; }
    public void setDbVersionId(Long dbVersionId) { this.dbVersionId = dbVersionId; }
    public Long getApiId() { return apiId; }
    public void setApiId(Long apiId) { this.apiId = apiId; }
    public Long getApiVersionId() { return apiVersionId; }
    public void setApiVersionId(Long apiVersionId) { this.apiVersionId = apiVersionId; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getScriptVersionId() { return scriptVersionId; }
    public void setScriptVersionId(Long scriptVersionId) { this.scriptVersionId = scriptVersionId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
}
