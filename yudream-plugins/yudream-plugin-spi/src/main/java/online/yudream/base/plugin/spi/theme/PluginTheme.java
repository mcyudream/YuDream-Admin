package online.yudream.base.plugin.spi.theme;

import java.util.List;
import java.util.Set;

/**
 * 插件主题注册项：一套可整体换肤的界面皮肤声明。
 *
 * <p>一个插件最多注册一个主题；同一 {@link PluginThemeScope} 下同时只有一个
 * 主题插件处于启用状态，启用新主题插件时宿主自动禁用旧主题插件（自动顶替）。
 * 未启用任何主题插件的 scope 回落到宿主内置主题。</p>
 *
 * <p>styles 中的 CSS 文件必须打进插件 JAR 的
 * {@code META-INF/yudream-plugin/frontend/{pluginCode}/} 目录，经
 * {@code /api/platform/plugins/{pluginCode}/assets/**} 下发。作用域约定：
 * SITE 主题以 {@code .site-page}/{@code .site-chrome} 容器为作用域并覆写
 * {@code --yb-site-*} 变量；ADMIN 主题以 {@code :root}/{@code .dark} 为作用域
 * 覆写宿主全局变量。深浅两套配色由主题 CSS 自行通过 {@code .dark} 选择器提供。</p>
 *
 * <p>homePreset 可声明一份首页内容定制方案 JSON（同资产目录），SITE 主题激活时
 * 宿主会自动快照当前首页定制并应用该方案；方案只覆盖其声明的字段与 settings 键，
 * 未声明的（如站点导航）保留现状。</p>
 */
public record PluginTheme(
        String code,
        String name,
        String description,
        Set<PluginThemeScope> scopes,
        List<String> styles,
        String preview,
        String homePreset
) {
}
