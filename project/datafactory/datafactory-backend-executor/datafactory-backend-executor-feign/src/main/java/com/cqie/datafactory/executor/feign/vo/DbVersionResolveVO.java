package com.cqie.datafactory.executor.feign.vo;

public class DbVersionResolveVO {
    private Long id;
    private Long dbId;
    private String environment;
    private String jdbcUrl;
    private String username;
    private String password;
    private String dbType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
}
