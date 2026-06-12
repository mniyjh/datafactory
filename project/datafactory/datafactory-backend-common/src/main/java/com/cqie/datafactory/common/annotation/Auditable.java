package com.cqie.datafactory.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    /** 操作描述 */
    String value() default "";
}
