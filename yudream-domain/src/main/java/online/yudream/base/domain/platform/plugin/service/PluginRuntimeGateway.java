package online.yudream.base.domain.platform.plugin.service;

import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;

import java.util.List;

public interface PluginRuntimeGateway {

    List<PluginDescriptorInfo> discover();

    void load(PluginModule module);

    void enable(PluginModule module);

    void disable(String code);

    void unload(String code);

    boolean loaded(String code);

    boolean enabled(String code);

    List<PluginPermissionInfo> permissions(String code);

    List<PluginFrontendModuleInfo> frontendModules();

    PluginHttpDispatchResult dispatch(PluginHttpDispatchRequest request);
}
