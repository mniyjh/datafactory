package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;

@FunctionalInterface
public interface DslValidationRule {
    void validate(DslModel dsl);
}
