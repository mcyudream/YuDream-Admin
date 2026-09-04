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
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;
import online.yudream.base.plugin.spi.system.graph.PluginGraphService;
import online.yudream.base.plugin.spi.system.preview.PluginFilePreviewService;

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

    default PluginSecretStore secrets() {
        return framework().secrets(pluginCode());
    }

    /** 平台文件预览能力（kkFileView 统一配置 + 签名文件地址签发）。 */
    default PluginFilePreviewService filePreview() {
        return framework().filePreview();
    }

    PluginGraphService graph();

    PluginMessageInteractionRegistry interactions();

    PluginCommandRegistry commands();

    PluginTemplateRenderService templateRenderer();

    PluginSemanticMemoryService semanticMemory();

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

    /**
     * 调整本插件路由菜单在侧边栏的可见性（按路由路径匹配，如 /platform/plugins/xxx）。
     * 用于功能开关联动入口显隐；匹配不到菜单记录或宿主不支持时静默忽略。
     */
    default void setMenuVisible(String routePath, boolean visible) {
    }

    void onDispose(AutoCloseable closeable);
}
