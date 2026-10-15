package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明插件的新手引导：宿主在用户进入 {@code routePath} 匹配页面且未完成时自动播放，
 * 也可由全局 AI 前端工具触发。步骤目标必须是插件页面中以 {@code data-tour} 登记
 * 的稳定元素，宿主不执行任意选择器。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PluginGuides.class)
public @interface PluginGuide {

    /** 引导编码，插件内唯一。 */
    String code();

    /** 引导标题。 */
    String title();

    /** 引导自动播放的页面路径，如 /platform/plugins/xxx。 */
    String routePath();

    /** 可见所需权限；为空表示登录即可见。 */
    String permission() default "";

    int sort() default 500;

    PluginGuideStep[] steps();
}
