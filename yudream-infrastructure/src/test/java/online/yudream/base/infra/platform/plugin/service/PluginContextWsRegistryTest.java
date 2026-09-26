package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.ws.PluginWsHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PluginContext WS 注册表：注册/解析（精确与模式匹配）/重复拒绝/随运行时贡献清理。
 * 通过 SPI 接口注册，验证第三方实现兼容入口与宿主实现解析行为。
 * 放在 infrastructure 测试源（PluginContextImpl 属 infra，interfaces 测试不可见）。
 */
class PluginContextWsRegistryTest {

    private PluginContextImpl context;

    @BeforeEach
    void setUp() {
        // frameworkServices 传测试替身、pluginServiceRegistry 用真实最小实例：
        // clearRuntimeContributions 会调用 registry.clear(pluginCode)，传 null 会以 NPE 掩盖真实断言
        context = new PluginContextImpl("demo", null, frameworkServices(),
                new PluginServiceRegistry(), java.util.Set.of(), code -> true,
                null, null, null, null, null, null);
    }

    /** 测试替身：所有框架服务返回 null（本测试不触达）。 */
    private static FrameworkServices frameworkServices() {
        return new FrameworkServices() {
            @Override public online.yudream.base.plugin.spi.system.user.PluginUserService users() { return null; }
            @Override public online.yudream.base.plugin.spi.system.user.PluginQqBindingService qqBindings() { return null; }
            @Override public online.yudream.base.plugin.spi.system.command.PluginCommandService commands() { return null; }
            @Override public online.yudream.base.plugin.spi.system.ai.PluginAiService ai() { return null; }
            @Override public online.yudream.base.plugin.spi.system.security.PluginSecurityService security() { return null; }
            @Override public online.yudream.base.plugin.spi.system.mail.PluginMailService mail() { return null; }
            @Override public online.yudream.base.plugin.spi.system.document.PluginWordTemplateService wordTemplates() { return null; }
            @Override public online.yudream.base.plugin.spi.system.storage.PluginDocumentStore documents(String pluginCode) { return null; }
            @Override public online.yudream.base.plugin.spi.system.storage.PluginFileStore files(String pluginCode) { return null; }
            @Override public online.yudream.base.plugin.spi.system.messaging.PluginMessagingService messaging() { return null; }
            @Override public online.yudream.base.plugin.spi.system.messaging.PluginMessagingRawService messagingRaw() { return null; }
            @Override public online.yudream.base.plugin.spi.system.render.PluginRenderService render() { return null; }
            @Override public java.util.Optional<online.yudream.base.plugin.spi.system.storage.PluginStoredFile> platformFile(String fileId) { return java.util.Optional.empty(); }
            @Override public java.util.Optional<String> setting(String key) { return java.util.Optional.empty(); }
        };

    }

    @Test
    @DisplayName("SPI 接口注册（带权限）→ 精确解析成功")
    void registerViaSpiInterface() {
        PluginWsHandler handler = (session, handshake) -> { };
        context.registerWebSocketHandler("/chat", "plugin:demo:use", handler);

        Optional<PluginContextImpl.WsHandlerRegistration> found = context.findWsHandler("/chat");
        assertTrue(found.isPresent());
        assertEquals("plugin:demo:use", found.get().permission());
        assertEquals(handler, found.get().handler());
        assertEquals("/chat", found.get().path());
    }

    @Test
    @DisplayName("无权限重载默认空权限")
    void registerWithoutPermission() {
        PluginWsHandler handler = (session, handshake) -> { };
        context.registerWebSocketHandler("/chat", handler);
        assertEquals("", context.findWsHandler("/chat").orElseThrow().permission());
    }

    @Test
    @DisplayName("模式路由匹配：{var}、*、**")
    void patternMatching() {
        PluginWsHandler handler = (session, handshake) -> { };
        context.registerWebSocketHandler("/room/{roomId}", "", handler);
        context.registerWebSocketHandler("/wild/*/end", "", handler);
        context.registerWebSocketHandler("/deep/**", "", handler);

        assertTrue(context.findWsHandler("/room/123").isPresent());
        assertTrue(context.findWsHandler("/wild/abc/end").isPresent());
        assertTrue(context.findWsHandler("/deep/a/b/c").isPresent());
        assertTrue(context.findWsHandler("/room").isEmpty());
    }

    @Test
    @DisplayName("重复路径注册拒绝")
    void duplicateRejected() {
        PluginWsHandler handler = (session, handshake) -> { };
        context.registerWebSocketHandler("/chat", "", handler);
        assertThrows(RuntimeException.class, () -> context.registerWebSocketHandler("/chat", "", handler));
    }

    @Test
    @DisplayName("随 clearRuntimeContributions 清理（disable/unload 生效，可重新注册）")
    void clearedOnDispose() {
        PluginWsHandler handler = (session, handshake) -> { };
        context.registerWebSocketHandler("/chat", "", handler);
        assertTrue(context.findWsHandler("/chat").isPresent());
        context.clearRuntimeContributions();
        assertTrue(context.findWsHandler("/chat").isEmpty());
        // disable → re-enable 场景：清理后同一路径可重新注册（wsHandlers.clear 回归）
        context.registerWebSocketHandler("/chat", "", handler);
        assertTrue(context.findWsHandler("/chat").isPresent());
    }

    @Test
    @DisplayName("未实现契约的第三方 PluginContext：default 抛 UnsupportedOperationException")
    void defaultMethodThrowsOnForeignImplementation() {
        PluginContext foreign = new PluginContext() {
            @Override public String pluginCode() { return "foreign"; }
            @Override public online.yudream.base.plugin.spi.system.FrameworkServices framework() { return null; }
            @Override public online.yudream.base.plugin.spi.system.graph.PluginGraphService graph() { return null; }
            @Override public online.yudream.base.plugin.spi.system.messaging.PluginMessageInteractionRegistry interactions() { return null; }
            @Override public online.yudream.base.plugin.spi.system.command.PluginCommandRegistry commands() { return null; }
            @Override public online.yudream.base.plugin.spi.system.render.PluginTemplateRenderService templateRenderer() { return null; }
            @Override public online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService semanticMemory() { return null; }
            @Override public void registerMenu(online.yudream.base.plugin.spi.menu.PluginMenuItem item) { }
            @Override public void registerPermission(online.yudream.base.plugin.spi.permission.PluginPermissionItem item) { }
            @Override public void registerCapability(online.yudream.base.plugin.spi.capability.PluginCapabilityItem item) { }
            @Override public void registerDashboardCard(online.yudream.base.plugin.spi.dashboard.PluginDashboardCard card) { }
            @Override public void registerGlobalWidget(online.yudream.base.plugin.spi.widget.PluginGlobalWidget widget) { }
            @Override public void registerTheme(online.yudream.base.plugin.spi.theme.PluginTheme theme) { }
            @Override public void registerFrontend(online.yudream.base.plugin.spi.frontend.PluginFrontendModule module) { }
            @Override public void registerHttpHandler(String method, String path, online.yudream.base.plugin.spi.http.PluginHttpHandler handler) { }
            @Override public void registerHttpController(Object controller) { }
            @Override public void registerAiTool(online.yudream.base.plugin.spi.system.ai.PluginAiTool tool) { }
            @Override public <I> void registerExtension(Class<I> extensionPoint, I extension, int priority) { }
            @Override public <I> List<I> extensions(Class<I> extensionPoint) { return List.of(); }
            @Override public <T> void exposeService(Class<T> serviceType, T service) { }
            @Override public <T> java.util.Optional<T> service(String pluginCode, Class<T> serviceType) { return java.util.Optional.empty(); }
            @Override public <T> List<T> services(Class<T> serviceType) { return List.of(); }
            @Override public boolean dependencyAvailable(String pluginCode) { return false; }
            @Override public void onDispose(AutoCloseable closeable) { }
        };
        assertThrows(UnsupportedOperationException.class,
                () -> foreign.registerWebSocketHandler("/chat", "p", (session, handshake) -> { }));
        assertThrows(UnsupportedOperationException.class,
                () -> foreign.registerWebSocketHandler("/chat", (session, handshake) -> { }));
    }
}
