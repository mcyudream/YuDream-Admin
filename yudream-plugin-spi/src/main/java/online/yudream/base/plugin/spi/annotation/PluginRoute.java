package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginRoute {

    String path();

    String name();

    String title();

    String icon() default "";

    String parentPath() default "";

    String parentTitle() default "";

    String parentIcon() default "";

    int parentSort() default 0;

    String component();

    String permission() default "";

    int sort() default 0;
}
