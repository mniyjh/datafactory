package com.cqie.datafactory.executor.feign.vo;

public class ScriptExecutionVO {
    private Long id;
    private Long scriptId;
    private String scriptCode;
    private String scriptName;
    private String scriptType;
    private String version;
    private String environment;
    private String scriptCodeContent;
    private Integer timeout;
    private Integer retryCount;
    private String dependencies;
    private String envVars;
    private String workDir;
    private String interpreterPath;
    private Integer maxMemory;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public String getScriptCode() { return scriptCode; }
    public void setScriptCode(String scriptCode) { this.scriptCode = scriptCode; }
    public String getScriptName() { return scriptName; }
    public void setScriptName(String scriptName) { this.scriptName = scriptName; }
    public String getScriptType() { return scriptType; }
    public void setScriptType(String scriptType) { this.scriptType = scriptType; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getScriptCodeContent() { return scriptCodeContent; }
    public void setScriptCodeContent(String scriptCodeContent) { this.scriptCodeContent = scriptCodeContent; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getDependencies() { return dependencies; }
    public void setDependencies(String dependencies) { this.dependencies = dependencies; }
    public String getEnvVars() { return envVars; }
    public void setEnvVars(String envVars) { this.envVars = envVars; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public String getInterpreterPath() { return interpreterPath; }
    public void setInterpreterPath(String interpreterPath) { this.interpreterPath = interpreterPath; }
    public Integer getMaxMemory() { return maxMemory; }
    public void setMaxMemory(Integer maxMemory) { this.maxMemory = maxMemory; }
}
