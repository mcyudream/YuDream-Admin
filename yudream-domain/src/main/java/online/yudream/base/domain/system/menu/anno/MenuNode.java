package online.yudream.base.domain.system.menu.anno;

import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 菜单节点注解，标注在枚举常量上，描述一个菜单或按钮节点。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MenuNode {

    /**
     * 节点唯一标识，默认也作为权限码。
     */
    String code();

    /**
     * 菜单/按钮名称。
     */
    String name();

    /**
     * 节点类型：CATEGORY（分组）、LAYOUT（布局）、MENU（页面）、LINK（外链）、BUTTON（按钮权限）。
     */
    MenuNodeType type() default MenuNodeType.MENU;

    /**
     * 父节点所在枚举类。默认 Object.class 表示当前枚举类。
     */
    Class<?> parentClass() default Object.class;

    /**
     * 父节点在该枚举类中的常量名。为空表示挂在模块根下。
     */
    String parentName() default "";

    /**
     * 父节点 code 兜底字段，仅在无法通过 parentClass/parentName 解析时使用。
     */
    String parentCode() default "";

    /**
     * 所属模块 code，可覆盖枚举类上的 @MenuModule。为空则继承模块 code。
     */
    String module() default "";

    /**
     * 节点图标。
     */
    String icon() default "";

    /**
     * 路由路径。对 CATEGORY/MENU 有效。
     */
    String path() default "";

    /**
     * 前端组件路径，相对于 src/views/；Layout 表示布局组件。对 MENU 有效。
     */
    String component() default "";

    /**
     * 外链地址。对 LINK 有效。
     */
    String link() default "";

    /**
     * 排序权重，越大越靠前。
     */
    int sort() default 0;

    /**
     * 是否在导航菜单中显示。隐藏节点仍然会生成路由并参与权限控制。
     */
    boolean visible() default true;

    /**
     * 显式权限码；为空时继承 code。
     */
    String permission() default "";
}
