package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.valobj.PluginCapabilityAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMenuAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMessageInteractionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginAiToolInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAgentInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个插件运行时贡献资产快照的传输对象，清单元素为领域快照记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginRuntimeAssetsDTO {

    private String pluginCode;
    private boolean loaded;
    private boolean enabled;

    @Builder.Default
    private List<PluginMenuAssetInfo> menus = new ArrayList<>();
    @Builder.Default
    private List<PluginPermissionInfo> permissions = new ArrayList<>();
    @Builder.Default
    private List<PluginCapabilityAssetInfo> capabilities = new ArrayList<>();
    @Builder.Default
    private List<PluginDashboardCardInfo> dashboardCards = new ArrayList<>();
    @Builder.Default
    private List<PluginFrontendModuleInfo> frontendModules = new ArrayList<>();
    @Builder.Default
    private List<PluginHttpEndpointInfo> httpEndpoints = new ArrayList<>();
    @Builder.Default
    private List<PluginCommandInfo> commands = new ArrayList<>();
    @Builder.Default
    private List<PluginMessageInteractionInfo> messageInteractions = new ArrayList<>();
    @Builder.Default
    private List<PluginAiToolInfo> aiTools = new ArrayList<>();
    @Builder.Default
    private List<PluginRuntimeAgentInfo> agents = new ArrayList<>();
    @Builder.Default
    private List<String> exposedServices = new ArrayList<>();
}
