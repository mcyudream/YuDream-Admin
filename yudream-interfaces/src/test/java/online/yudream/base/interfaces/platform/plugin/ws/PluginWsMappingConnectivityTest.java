package online.yudream.base.interfaces.platform.plugin.ws;

import jakarta.servlet.http.HttpServletResponse;
import online.yudream.base.interfaces.platform.plugin.controller.PluginController;
import online.yudream.base.interfaces.platform.plugin.controller.PluginDispatchController;
import online.yudream.base.interfaces.platform.plugin.controller.PluginWsTicketController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestHandler;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 HandlerMapping 连通性与路径边界（最终冻结路径 /api/platform/plugin-ws/**）：
 * <ul>
 *   <li>WS 映射（order=-100）在真实 MVC 注解映射存在的情况下优先拿到升级请求；</li>
 *   <li>非升级请求命中 WS 前缀时由升级守卫 404；</li>
 *   <li>旧疑冲突路径 /api/platform/plugins/ws/assets/**（code=ws 的资产路由形状）
 *       保持 MVC 可达、WS 映射完全不触碰（前缀隔离实证）；</li>
 *   <li>票据端点 /api/platform/plugin-ws-ticket 在 WS 前缀之外可达；</li>
 *   <li>插件 HTTP 命名空间 /api/plugins/** 不受影响；</li>
 *   <li>容器 Bean 不设置全局会话空闲超时（不侵入 AGUI 等既有平台 WS）。</li>
 * </ul>
 */
class PluginWsMappingConnectivityTest {

    private StaticApplicationContext context;
    private RequestMappingHandlerMapping mvc;
    private SimpleUrlHandlerMapping pluginWs;

    @BeforeEach
    void setUp() {
        context = new StaticApplicationContext();
        context.registerBean("dispatchController", PluginDispatchController.class,
                () -> new PluginDispatchController(null, null, null));
        context.registerBean("pluginController", PluginController.class,
                () -> new PluginController(null, null, null));
        context.registerBean("ticketController", PluginWsTicketController.class,
                () -> new PluginWsTicketController(null, null));
        context.refresh();

        PluginWsProperties properties = new PluginWsProperties();
        PluginWsBridgeHandler bridge = new PluginWsBridgeHandler(properties, (code, path) -> Optional.empty());
        PluginWsHandshakeInterceptor interceptor = new PluginWsHandshakeInterceptor(
                (code, path) -> Optional.empty(),
                new WsAuthSupport() {
                    @Override
                    public online.yudream.base.plugin.spi.system.security.PluginPrincipal fromRequestContext(
                            jakarta.servlet.http.HttpServletRequest request,
                            java.util.function.Consumer<CredentialSource> credentialSource) {
                        credentialSource.accept(CredentialSource.ANONYMOUS);
                        return null;
                    }

                    @Override
                    public List<String> permissions(Long userId) {
                        return List.of();
                    }
                },
                new PluginWsTicketService(),
                new PluginWsOriginPolicy(List.of()));
        WebSocketHttpRequestHandler wsHandler = new WebSocketHttpRequestHandler(bridge,
                new DefaultHandshakeHandler());
        wsHandler.setHandshakeInterceptors(List.of(interceptor));
        HttpRequestHandler guard = (request, response) -> {
            String upgrade = request.getHeader("Upgrade");
            if (upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim())) {
                wsHandler.handleRequest(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        };
        // SimpleUrlHandlerMapping 不是 InitializingBean：urlMap → handlerMap 的注册发生在
        // ApplicationObjectSupport.setApplicationContext（公开 final）触发的
        // initApplicationContext → registerHandlers；直接传入已有 context 即完成初始化
        pluginWs = new SimpleUrlHandlerMapping(
                java.util.Map.of(PluginWsPaths.WS_PATTERN, guard), PluginWsPaths.MAPPING_ORDER);
        pluginWs.setApplicationContext(context);

        mvc = new RequestMappingHandlerMapping();
        mvc.setApplicationContext(context);
        mvc.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static MockHttpServletRequest upgrade(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Connection", "Upgrade");
        request.addHeader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==");
        request.addHeader("Sec-WebSocket-Version", "13");
        return request;
    }

    @Test
    @DisplayName("升级请求：WS 映射（order=-100）先于 MVC 命中真实握手处理器")
    void upgradeRequestResolvedByWsMappingWithDeterministicOrder() throws Exception {
        HandlerExecutionChain chain = pluginWs.getHandler(
                upgrade("GET", "/api/platform/plugin-ws/demo/chat"));
        assertNotNull(chain);
        assertTrue(chain.getHandler() instanceof HttpRequestHandler, "应为升级守卫 HttpRequestHandler");

        List<HandlerMapping> ordered = new ArrayList<>(List.of(mvc, pluginWs));
        ordered.sort(Comparator.comparingInt(h -> h instanceof SimpleUrlHandlerMapping s ? s.getOrder() : 0));
        assertEquals(pluginWs, ordered.get(0), "WS 映射必须先于 MVC 解析");
    }

    @Test
    @DisplayName("非升级请求命中 WS 前缀：守卫 404")
    void nonUpgradeRequestGuarded() throws Exception {
        String path = "/api/platform/plugin-ws/demo/chat";
        HandlerExecutionChain chain = pluginWs.getHandler(new MockHttpServletRequest("GET", path));
        assertNotNull(chain);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ((HttpRequestHandler) chain.getHandler()).handleRequest(
                new MockHttpServletRequest("GET", path), response);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("旧疑冲突路径（code=ws 资产形状）不被 WS 前缀触碰：MVC 保持可达")
    void legacyAssetsShapeStaysWithMvc() throws Exception {
        String assetsPath = "/api/platform/plugins/ws/assets/plugin-icon.svg";
        assertNull(pluginWs.getHandler(new MockHttpServletRequest("GET", assetsPath)),
                "WS 前缀 /api/platform/plugin-ws/** 不得匹配 /api/platform/plugins/**");
        assertNotNull(mvc.getHandler(new MockHttpServletRequest("GET", assetsPath)),
                "code=ws 的资产路由必须继续由 MVC 资产控制器承接");
    }

    @Test
    @DisplayName("票据端点 /api/platform/plugin-ws-ticket：WS 映射不截胡，MVC 可达")
    void ticketEndpointOutsideWsPrefix() throws Exception {
        String ticketUri = PluginWsPaths.TICKET_PATH;
        assertNull(pluginWs.getHandler(new MockHttpServletRequest("POST", ticketUri)),
                "票据路由不得落在 WS 前缀下，否则被 order=-100 守卫 404");
        assertNotNull(mvc.getHandler(new MockHttpServletRequest("POST", ticketUri)),
                "票据端点必须由 MVC 真实承接");
    }

    @Test
    @DisplayName("插件 HTTP 命名空间 /api/plugins/** 完全不受 WS 映射影响")
    void pluginHttpNamespaceUntouched() throws Exception {
        String pluginHttpUri = "/api/plugins/demo/anything";
        assertNull(pluginWs.getHandler(new MockHttpServletRequest("GET", pluginHttpUri)));
        assertNotNull(mvc.getHandler(new MockHttpServletRequest("GET", pluginHttpUri)),
                "插件派发控制器应继续承接插件 HTTP 路径");
    }

    @Test
    @DisplayName("容器 Bean 不设置全局会话空闲超时（不侵入 AGUI 等既有平台 WS）")
    void containerBeanDoesNotTouchGlobalIdleTimeout() {
        PluginWsMappingConfiguration configuration = new PluginWsMappingConfiguration();
        var container = configuration.pluginWsServletContainer(new PluginWsProperties());
        assertNull(container.getMaxSessionIdleTimeout(), "不得设置全局 idle 超时");
        assertEquals(1024 * 1024, container.getMaxTextMessageBufferSize());
        assertEquals(1024 * 1024, container.getMaxBinaryMessageBufferSize());
    }
}
