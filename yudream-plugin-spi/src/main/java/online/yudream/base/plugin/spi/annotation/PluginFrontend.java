package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginFrontend {

    String entry() default "";

    String moduleName();

    String sdkVersion() default "";

    String integrity() default "";

    PluginRoute[] routes() default {};
}
