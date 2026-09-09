package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.Set;

/**
 * 插件主题运行时信息：已启用插件声明的界面主题。
 * scopes 取值为 SITE / ADMIN；styles 与 preview 为插件前端资产相对路径，
 * assetRevision 用于资产缓存失效。
 */
public record PluginThemeInfo(
        String pluginCode,
        String code,
        String name,
        String description,
        Set<String> scopes,
        List<String> styles,
        String preview,
        String assetRevision
) {
}
