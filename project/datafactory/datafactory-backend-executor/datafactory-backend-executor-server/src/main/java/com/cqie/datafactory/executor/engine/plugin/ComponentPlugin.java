package com.cqie.datafactory.executor.engine.plugin;

import java.util.Map;
import java.util.Set;

public interface ComponentPlugin {
    Set<String> supportedTypes();
    Map<String, Object> execute(PluginContext context);
    default boolean isEnabled() { return true; }
}
