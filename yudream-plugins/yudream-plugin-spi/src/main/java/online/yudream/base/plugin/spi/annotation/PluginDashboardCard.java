package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PluginDashboardCards.class)
public @interface PluginDashboardCard {

    String code();

    String title();

    String description() default "";

    String icon() default "";

    String category() default "插件";

    String permission() default "";

    String component() default "ACTION_CARD";

    String actionPath() default "";

    String dragPayloadTemplate() default "";

    String tone() default "blue";

    int defaultW() default 4;

    int defaultH() default 3;

    int minW() default 3;

    int minH() default 2;

    int sort() default 500;

    /**
     * 将卡片作为首次访问首页的默认指引；用户保存个人布局后不再自动展示。
     */
    boolean defaultOnFirstVisit() default false;
}
