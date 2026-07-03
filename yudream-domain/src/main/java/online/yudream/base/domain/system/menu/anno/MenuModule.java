package online.yudream.base.domain.system.menu.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 菜单模块注解，标注在枚举类上，表示该枚举类对应前端的一个主导航模块。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MenuModule {

    /**
     * 模块唯一标识，也作为顶层菜单分组 code。
     */
    String code();

    /**
     * 模块名称，对应前端主导航标题。
     */
    String name();

    /**
     * 模块图标，使用 UnoCSS / Iconify 类名，如 i-ri:settings-3-line。
     */
    String icon() default "";

    /**
     * 排序权重，越大越靠前。
     */
    int sort() default 0;
}
