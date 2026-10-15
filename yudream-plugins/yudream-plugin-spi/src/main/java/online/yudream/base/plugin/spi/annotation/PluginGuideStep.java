package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明引导的单个步骤：{@code target} 推荐使用页面元素上的
 * {@code data-tour="..."} 标识并以 {@code [data-tour="..."]} 选择器引用；
 * 为空表示该步骤居中展示。
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginGuideStep {

    /** 目标元素选择器，推荐 [data-tour="..."]；为空表示居中步骤。 */
    String target() default "";

    /** 步骤标题。 */
    String title() default "";

    /** 步骤说明文案，必须自含完整语义。 */
    String content();

    /** 气泡方位：top/bottom/left/right，默认 bottom。 */
    String placement() default "bottom";
}
