package com.cqie.datafactory.configuration.controller.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.entity.Component;
import com.cqie.datafactory.configuration.service.ComponentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/feign/component")
public class ComponentFeignController {

    private final ComponentService componentService;

    public ComponentFeignController(ComponentService componentService) {
        this.componentService = componentService;
    }

    @GetMapping("/{id}/validate")
    public Result<Map<String, Object>> validateAndGet(@PathVariable Long id) {
        Component component = componentService.getOne(new LambdaQueryWrapper<Component>()
                .eq(Component::getId, id));

        if (component == null) {
            return Result.fail("组件不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", component.getId());
        result.put("componentCode", component.getComponentCode());
        result.put("componentName", component.getComponentName());
        result.put("componentType", component.getComponentType());
        result.put("version", component.getVersion());
        result.put("status", component.getStatus());
        return Result.success(result);
    }
}
