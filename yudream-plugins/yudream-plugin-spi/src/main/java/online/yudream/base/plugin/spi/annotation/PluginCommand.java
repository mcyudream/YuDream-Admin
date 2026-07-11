package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a message command handled by a plugin method. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PluginCommands.class)
public @interface PluginCommand {
    String code();
    String command();
    String name();
    String permission() default "";
    String description() default "";
    boolean allowAnonymous() default false;
}
