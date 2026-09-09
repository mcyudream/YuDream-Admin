package online.yudream.base.plugin.spi.annotation;

import online.yudream.base.plugin.spi.theme.PluginThemeScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明插件提供的界面主题。一个插件最多声明一个主题（不可重复标注）。
 *
 * <p>启用主题插件会自动顶替同 scope 下已启用的其他主题插件；禁用/卸载后
 * 该 scope 回落到宿主内置主题。主题 CSS 资产必须打进插件 JAR 的
 * {@code META-INF/yudream-plugin/frontend/{pluginCode}/} 目录。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginTheme {

    /** 主题编码，插件内唯一标识。 */
    String code();

    /** 主题展示名。 */
    String name();

    /** 主题简介，可为空。 */
    String description() default "";

    /** 主题生效范围：公开站 / 管理后台，至少声明一个。 */
    PluginThemeScope[] scopes();

    /** 主题 CSS 资产相对路径（相对前端资产根目录），按声明顺序注入。 */
    String[] styles();

    /** 预览图资产相对路径，可为空。 */
    String preview() default "";
}
