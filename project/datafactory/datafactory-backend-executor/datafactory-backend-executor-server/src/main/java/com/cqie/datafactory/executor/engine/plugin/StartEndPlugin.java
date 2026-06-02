package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class StartEndPlugin implements ComponentPlugin {

    @Override
    public Set<String> supportedTypes() { return Set.of("START", "END"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        Map<String, Object> outputs = new HashMap<>();
        for (IoParamDef def : context.getNode().getOutputParams()) {
            String code = def.getParamCode();
            Object resolvedVal = context.getResolvedInputs().get(code);
            if (resolvedVal != null && !(resolvedVal instanceof Map)) {
                outputs.put(code, resolvedVal);
            } else if (def.getDefaultValue() != null && !def.getDefaultValue().isBlank()) {
                outputs.put(code, def.getDefaultValue());
            } else if (def.getSourceValue() != null && !def.getSourceValue().isNull()
                    && def.getSourceValue().isTextual()) {
                outputs.put(code, def.getSourceValue().asText());
            } else {
                outputs.put(code, null);
            }
        }
        if (outputs.isEmpty()) {
            outputs.putAll(context.getResolvedInputs());
        }
        return outputs;
    }
}
