package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PluginRegistry {
    private final Map<String, ComponentPlugin> plugins = new HashMap<>();

    public PluginRegistry(List<ComponentPlugin> pluginList) {
        for (ComponentPlugin plugin : pluginList) {
            for (String type : plugin.supportedTypes()) {
                plugins.put(type.toUpperCase(), plugin);
            }
        }
    }

    public boolean has(String type) {
        return plugins.containsKey(type.toUpperCase());
    }

    public ComponentPlugin get(String type) {
        return Optional.ofNullable(plugins.get(type.toUpperCase()))
                .orElseThrow(() -> new BusinessException("不支持的组件类型: " + type));
    }
}
