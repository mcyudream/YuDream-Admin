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

    /**
     * 首页内容定制方案 JSON 资产相对路径，可为空。
     * SITE 主题激活时宿主自动快照当前首页定制并应用该方案；
     * 方案只覆盖其声明的字段与 settings 键，未声明的保留现状。
     */
    String homePreset() default "";

    /**
     * 主题配置 schema JSON 资产相对路径，可为空。
     * 声明后宿主主题中心会为该主题渲染独立的配置大页面（WordPress 自定义器形态），
     * 配置值按主题持久化并在公开站模板中以 {@code theme.config.*} 注入。
     */
    String configSchema() default "";
}
