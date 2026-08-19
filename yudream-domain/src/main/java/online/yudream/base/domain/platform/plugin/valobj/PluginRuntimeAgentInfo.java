package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 插件声明的运行时 Agent 资产；id 按长 ID 规则以字符串承载。
 */
public record PluginRuntimeAgentInfo(
        String pluginCode,
        String id,
        String code,
        String name,
        String description,
        String icon,
        String status
) {
}
