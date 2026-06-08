package com.core.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares security roles required to execute the annotated controller method or class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SecuredWorkflow {
    
    /**
     * The roles required to execute the action (e.g., {"ADMIN", "OPERATOR"}).
     *
     * @return array of required role strings
     */
    String[] value() default {};
}
