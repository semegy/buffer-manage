package com;

import java.lang.annotation.*;

@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProviderService {

    Class<?> interfaceClass() default void.class;

    String version() default "";

    String group() default "";

    String methods() default "";
}
