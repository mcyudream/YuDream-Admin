package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/**
 * 单个插件运行时贡献的资产快照，供开发者工具面板聚合展示。
 * 禁用/卸载后运行时贡献已被回收，各清单自然为空，loaded/enabled 用于区分状态。
 */
public record PluginRuntimeAssets(
        String pluginCode,
        boolean loaded,
        boolean enabled,
        List<PluginMenuAssetInfo> menus,
        List<PluginPermissionInfo> permissions,
        List<PluginCapabilityAssetInfo> capabilities,
        List<PluginDashboardCardInfo> dashboardCards,
        List<PluginFrontendModuleInfo> frontendModules,
        List<PluginHttpEndpointInfo> httpEndpoints,
        List<PluginCommandInfo> commands,
        List<PluginMessageInteractionInfo> messageInteractions,
        List<PluginAiToolInfo> aiTools,
        List<PluginRuntimeAgentInfo> agents,
        List<String> exposedServices
) {
    public PluginRuntimeAssets {
        menus = menus == null ? List.of() : List.copyOf(menus);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        dashboardCards = dashboardCards == null ? List.of() : List.copyOf(dashboardCards);
        frontendModules = frontendModules == null ? List.of() : List.copyOf(frontendModules);
        httpEndpoints = httpEndpoints == null ? List.of() : List.copyOf(httpEndpoints);
        commands = commands == null ? List.of() : List.copyOf(commands);
        messageInteractions = messageInteractions == null ? List.of() : List.copyOf(messageInteractions);
        aiTools = aiTools == null ? List.of() : List.copyOf(aiTools);
        agents = agents == null ? List.of() : List.copyOf(agents);
        exposedServices = exposedServices == null ? List.of() : List.copyOf(exposedServices);
    }

    public static PluginRuntimeAssets unloaded(String pluginCode) {
        return new PluginRuntimeAssets(pluginCode, false, false, null, null, null, null, null, null, null, null, null, null, null);
    }
}
