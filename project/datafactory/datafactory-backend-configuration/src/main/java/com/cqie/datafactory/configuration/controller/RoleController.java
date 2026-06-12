package com.cqie.datafactory.configuration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.entity.Role;
import com.cqie.datafactory.configuration.mapper.RoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleMapper roleMapper;

    @Operation(summary = "角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('user:write')")
    public Result<List<Role>> list() {
        return Result.success(roleMapper.selectList(
                new LambdaQueryWrapper<Role>().eq(Role::getStatus, 1)));
    }
}
