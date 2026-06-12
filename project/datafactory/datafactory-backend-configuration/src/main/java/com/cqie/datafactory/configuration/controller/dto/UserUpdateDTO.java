package com.cqie.datafactory.configuration.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class UserUpdateDTO {
    private String realName;
    private String email;
    private String phone;
    private Integer status;       // 1=正常 0=禁用
    private List<Long> roleIds;

    @Size(min = 6, max = 128, message = "密码长度6-128位")
    private String password;      // 不填则不修改密码
}
