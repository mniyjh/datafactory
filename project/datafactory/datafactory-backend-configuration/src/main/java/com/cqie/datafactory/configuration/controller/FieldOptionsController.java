package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.FieldOptionsResolveDTO;
import com.cqie.datafactory.configuration.service.FieldOptionsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/component")
public class FieldOptionsController {

    private final FieldOptionsService fieldOptionsService;

    public FieldOptionsController(FieldOptionsService fieldOptionsService) {
        this.fieldOptionsService = fieldOptionsService;
    }

    @PostMapping("/field/options/resolve")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<List<Map<String, Object>>> resolveOptions(@RequestBody FieldOptionsResolveDTO dto) {
        return Result.success(fieldOptionsService.resolve(dto));
    }
}
