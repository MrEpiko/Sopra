package me.mrepiko.sopra.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SopraTable {
    String dataSourceId();
    String name() default "";
    boolean snakeCase() default false;
}
