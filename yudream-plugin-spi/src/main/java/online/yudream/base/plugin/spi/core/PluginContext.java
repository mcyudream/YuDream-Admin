package online.yudream.base.plugin.spi.core;

import online.yudream.base.plugin.spi.capability.PluginCapabilityItem;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.menu.PluginMenuItem;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;

import java.util.Optional;

public interface PluginContext {

    String pluginCode();

    FrameworkServices framework();

    default PluginDocumentStore documents() {
        return framework().documents(pluginCode());
    }

    default PluginFileStore files() {
        return framework().files(pluginCode());
    }

    void registerMenu(PluginMenuItem item);

    void registerPermission(PluginPermissionItem item);

    void registerCapability(PluginCapabilityItem item);

    void registerFrontend(PluginFrontendModule module);

    void registerHttpHandler(String method, String path, PluginHttpHandler handler);

    <T> void registerExtension(Class<T> type, T extension);

    <T> Optional<T> getExtension(Class<T> type);

    void onDispose(AutoCloseable closeable);
}
