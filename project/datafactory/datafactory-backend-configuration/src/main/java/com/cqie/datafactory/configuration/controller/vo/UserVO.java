package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private List<RoleInfo> roles;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    @Data
    public static class RoleInfo {
        private Long id;
        private String name;
        private String code;
    }
}
