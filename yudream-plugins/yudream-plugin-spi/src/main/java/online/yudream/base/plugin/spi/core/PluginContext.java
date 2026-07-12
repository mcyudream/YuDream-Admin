package online.yudream.base.plugin.spi.core;

import online.yudream.base.plugin.spi.capability.PluginCapabilityItem;
import online.yudream.base.plugin.spi.dashboard.PluginDashboardCard;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.menu.PluginMenuItem;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageInteractionRegistry;
import online.yudream.base.plugin.spi.system.command.PluginCommandRegistry;
import online.yudream.base.plugin.spi.system.render.PluginTemplateRenderService;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;

import java.util.Optional;
import java.util.List;

public interface PluginContext {

    String pluginCode();

    FrameworkServices framework();

    default PluginDocumentStore documents() {
        return framework().documents(pluginCode());
    }

    default PluginFileStore files() {
        return framework().files(pluginCode());
    }

    PluginMessageInteractionRegistry interactions();

    PluginCommandRegistry commands();

    PluginTemplateRenderService templateRenderer();

    void registerMenu(PluginMenuItem item);

    void registerPermission(PluginPermissionItem item);

    void registerCapability(PluginCapabilityItem item);

    void registerDashboardCard(PluginDashboardCard card);

    void registerFrontend(PluginFrontendModule module);

    void registerHttpHandler(String method, String path, PluginHttpHandler handler);

    void registerHttpController(Object controller);

    void registerAiTool(PluginAiTool tool);

    <T> void exposeService(Class<T> serviceType, T service);

    <T> Optional<T> service(String pluginCode, Class<T> serviceType);

    <T> List<T> services(Class<T> serviceType);

    boolean dependencyAvailable(String pluginCode);

    void onDispose(AutoCloseable closeable);
}
