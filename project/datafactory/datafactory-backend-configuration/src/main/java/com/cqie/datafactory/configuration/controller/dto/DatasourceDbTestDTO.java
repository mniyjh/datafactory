package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DatasourceDbTestDTO {
  private String dbType;
  private String dbName;
  private String jdbcUrl;
  private String username;
  private String password;
}
