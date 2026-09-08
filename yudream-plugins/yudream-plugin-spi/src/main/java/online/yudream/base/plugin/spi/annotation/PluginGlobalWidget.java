package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明插件的全局挂件：宿主在登录后的控制台布局中主动加载该插件前端模块，
 * 并以 {@code component} 指定的远程导出组件常驻渲染（如网页宠物、全局助手）。
 * 挂件组件通过 props 接收 {@code sdk}；manifest 对匿名访客不下发挂件。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PluginGlobalWidgets.class)
public @interface PluginGlobalWidget {

    /** 挂件编码，插件内唯一。 */
    String code();

    /** 远程模块 routes 映射中的组件键，如 "mc-pet/GlobalPet"。 */
    String component();

    /** 可见所需权限；为空表示登录即可见。 */
    String permission() default "";

    int sort() default 500;
}
