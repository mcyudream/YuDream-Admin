package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PluginCapabilities.class)
public @interface PluginCapability {

    String code();

    String name();

    String type();

    String description() default "";

    String icon() default "";

    PluginConfigEntry[] defaultConfig() default {};

    String[] dependencies() default {};
}
