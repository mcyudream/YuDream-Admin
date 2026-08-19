package online.yudream.base.domain.platform.plugin.service;

import online.yudream.base.domain.platform.plugin.aggregate .PluginModule;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;

import java.util.List;
import java.util.Optional;
import java.nio.file.Path;

public interface PluginRuntimeGateway {

    List<PluginDescriptorInfo> discover();

    Optional<PluginDescriptorInfo> describe(Path jarPath);

    void load(PluginModule module);

    void enable(PluginModule module);

    void disable(String code);

    void unload(String code);

    boolean loaded(String code);

    boolean enabled(String code);

    List<PluginPermissionInfo> permissions(String code);

    List<PluginFrontendModuleInfo> frontendModules();

    List<PluginDashboardCardInfo> dashboardCards();

    List<PluginHttpEndpointInfo> httpEndpoints();

    List<PluginCommandInfo> commands();

    Optional<PluginFrontendAssetInfo> frontendAsset(String code, String assetPath);

    PluginHttpDispatchResult dispatch(PluginHttpDispatchRequest request);

    /** 单个插件运行时贡献资产快照，供开发者工具面板聚合展示。 */
    PluginRuntimeAssets runtimeAssets(String code);

    /** 插件是否来自开发模式的源码目录加载。 */
    default boolean devModePlugin(String code) {
        return false;
    }

    /** 开发模式配置的项目清单，未启用开发模式时为空。 */
    default List<PluginDevProjectInfo> devModeProjects() {
        return List.of();
    }
}
