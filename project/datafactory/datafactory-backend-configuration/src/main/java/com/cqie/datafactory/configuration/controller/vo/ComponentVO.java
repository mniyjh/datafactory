package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentVO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String category;
    private String version;
    private String status;
    private String desc;
    private LocalDateTime createdAt;
}
