package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScriptVO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String status;
    private String desc;
    private LocalDateTime createdAt;
}
