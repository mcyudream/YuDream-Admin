package online.yudream.base.plugin.spi.core;

import online.yudream.base.plugin.spi.capability.PluginCapabilityItem;
import online.yudream.base.plugin.spi.dashboard.PluginDashboardCard;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.menu.PluginMenuItem;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.theme.PluginTheme;
import online.yudream.base.plugin.spi.widget.PluginGlobalWidget;
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

    /**
     * 注册全局挂件：宿主登录后的控制台布局常驻挂载的远程组件声明，
     * 随插件 disable/unload 自动移除，无需手工注销。
     */
    void registerGlobalWidget(PluginGlobalWidget widget);

    /**
     * 注册界面主题：一个插件最多注册一个主题；同一 scope 同时只有一个主题插件
     * 处于启用状态（启用新主题插件自动顶替旧的）。随插件 disable/unload 自动移除，
     * 该 scope 回落到宿主内置主题，无需手工注销。
     */
    void registerTheme(PluginTheme theme);

    void registerFrontend(PluginFrontendModule module);

    void registerHttpHandler(String method, String path, PluginHttpHandler handler);

    void registerHttpController(Object controller);

    void registerAiTool(PluginAiTool tool);

    /**
     * 以默认优先级 0 注册一个扩展实现。扩展点接口 extensionPoint 可以由宿主
     * （如 system/auth 下的契约）或 provider 插件的 *.api 包定义；实现会随
     * 插件 disable/unload 自动从扩展管线移除，无需手工注销。
     */
    default <I> void registerExtension(Class<I> extensionPoint, I extension) {
        registerExtension(extensionPoint, extension, 0);
    }

    /**
     * 注册一个扩展实现并指定优先级，数值越小越先被消费（同优先级按注册先后）。
     */
    <I> void registerExtension(Class<I> extensionPoint, I extension, int priority);

    /**
     * 查询某个扩展点当前全部已启用插件注册的实现，按优先级从小到大排序。
     */
    <I> List<I> extensions(Class<I> extensionPoint);

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
