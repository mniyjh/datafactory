package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExternalApiVersionCreateDTO {
  private Long apiId;
  private String environment;
  private String version;
  private String dslContent;
  private String requestMethod;
  private String requestUrl;
  private String contentType;
  private String requestHeaders;
  private String queryParams;
  private String requestBody;
  private String authType;
  private String authConfig;
  private Integer timeout;
  private Integer retryCount;
  private String changeLog;
}
