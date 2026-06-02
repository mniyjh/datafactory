package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExternalApiVersionPromoteDTO {
    private Long sourceVersionId;
    private String fromEnvironment;
    private String toEnvironment;
    private String changeLog;
}
