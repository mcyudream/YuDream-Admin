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
}
