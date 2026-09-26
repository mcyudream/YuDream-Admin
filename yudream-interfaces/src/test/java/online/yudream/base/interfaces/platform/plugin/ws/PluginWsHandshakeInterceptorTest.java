package online.yudream.base.interfaces.platform.plugin.ws;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.ws.PluginWsHandler;
import online.yudream.base.plugin.spi.ws.PluginWsHandshake;
import online.yudream.base.plugin.spi.ws.PluginWsSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 握手拦截器：端点不可用 404、匿名/票据/权限判定、票据跨路径拒绝、
 * 权限交集（不可扩张）、身份不可复核拒绝、Origin 策略与敏感信息不透传。
 */
class PluginWsHandshakeInterceptorTest {

    private static final String CODE = "demo";
    private static final String PATH = "/chat";
    private static final String REQUEST_URI = "/api/platform/plugin-ws/demo/chat";
    private static final String PERMISSION = "plugin:demo:use";

    private PluginWsTicketService tickets;
    private PluginWsHandshakeInterceptor interceptor;
    private PluginPrincipal contextPrincipal;
    private Long verifiedUserId;

    private final PluginWsHandler handler = new PluginWsHandler() {
        @Override
        public void onOpen(PluginWsSession session, PluginWsHandshake handshake) {
        }
    };

    @BeforeEach
    void setUp() {
        tickets = new PluginWsTicketService();
        contextPrincipal = null;
        verifiedUserId = null;
        rebuildInterceptor(List.of("https://demo.example.com"));
    }

    private void rebuildInterceptor(List<String> allowedOrigins) {
        WsEndpointResolver resolver = (code, path) ->
                CODE.equals(code) && PATH.equals(path)
                        ? Optional.of(new PluginWsEndpointBinding(CODE, PATH, PERMISSION, handler))
                        : Optional.empty();
        WsAuthSupport authSupport = new WsAuthSupport() {
            @Override
            public PluginPrincipal fromRequestContext(HttpServletRequest request,
                                                      Consumer<CredentialSource> credentialSource) {
                if (contextPrincipal != null) {
                    credentialSource.accept(CredentialSource.AUTHORIZATION_CONTEXT);
                    return contextPrincipal;
                }
                credentialSource.accept(CredentialSource.ANONYMOUS);
                return null;
            }

            @Override
            public List<String> permissions(Long userId) {
                if (userId == null) {
                    return null;
                }
                return userId.equals(verifiedUserId) ? List.of(PERMISSION, "extra:perm") : null;
            }
        };
        interceptor = new PluginWsHandshakeInterceptor(resolver, authSupport, tickets,
                new PluginWsOriginPolicy(allowedOrigins));
    }

    private boolean handshake(MockHttpServletRequest request, Map<String, Object> attributes) {
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                new TextWebSocketHandler(),
                attributes);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", REQUEST_URI);
        // 与直连请求一致的 Host：MockHttpServletRequest 默认 localhost:80，需显式设置供同源比对
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        return request;
    }

    @Test
    @DisplayName("插件不存在或未启用 → 404")
    void unknownEndpointRejected() {
        assertFalse(handshake(new MockHttpServletRequest("GET", "/api/platform/plugin-ws/ghost/chat"), new HashMap<>()));
    }

    @Test
    @DisplayName("Origin 缺失且无 header 凭据 → 拒绝且票据不被消费")
    void missingOriginRejectedWithoutBurningTicket() {
        var issued = tickets.issue(7L, List.of(PERMISSION), CODE, PATH);
        assertFalse(handshake(request(), new HashMap<>()));
        // 票据仍在：补上同源 Origin 后可用
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "http://127.0.0.1:8080");
        request.addParameter(PluginWsTicketService.PARAM_NAME, issued.token());
        Map<String, Object> attributes = new HashMap<>();
        verifiedUserId = 7L;
        assertTrue(handshake(request, attributes));
        assertNotNull(attributes.get(PluginWsAttributes.PRINCIPAL));
    }

    @Test
    @DisplayName("票据通道放行：principal 携带交集权限，敏感头与票据不透传")
    void ticketGrantWithIntersectionAndRedaction() {
        var issued = tickets.issue(7L, List.of(PERMISSION), CODE, PATH);
        verifiedUserId = 7L;
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "http://127.0.0.1:8080");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("X-API-Key", "ak-secret");
        request.addHeader("Cookie", "satoken=xyz");
        request.addParameter(PluginWsTicketService.PARAM_NAME, issued.token());
        request.addParameter("Authorization", "token-in-query");
        request.addParameter("roomId", "42");
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(handshake(request, attributes));

        PluginPrincipal principal = (PluginPrincipal) attributes.get(PluginWsAttributes.PRINCIPAL);
        assertEquals(7L, principal.userId());
        assertEquals(List.of(PERMISSION), principal.permissions());

        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) attributes.get(PluginWsAttributes.HEADERS);
        assertFalse(headers.containsKey("Authorization"));
        assertFalse(headers.containsKey("Cookie"));
        assertFalse(headers.values().stream().flatMap(List::stream).anyMatch(v -> v.contains("secret-token")));
        assertFalse(headers.values().stream().flatMap(List::stream).anyMatch(v -> v.contains("ak-secret")));
        assertFalse(headers.containsKey("X-API-Key"));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> query = (Map<String, List<String>>) attributes.get(PluginWsAttributes.QUERY);
        assertFalse(query.containsKey(PluginWsTicketService.PARAM_NAME));
        assertFalse(query.containsKey("Authorization"), "会话令牌查询参数不得透传");
        assertEquals(List.of("42"), query.get("roomId"));
        assertEquals(PATH, attributes.get(PluginWsAttributes.PATH));
    }

    @Test
    @DisplayName("票据跨路径持有：消费失败回落匿名 → 受权限端点拒绝")
    void crossPathTicketFallsBackToAnonymous() {
        var issued = tickets.issue(7L, List.of(PERMISSION), CODE, "/other");
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "http://127.0.0.1:8080");
        request.addParameter(PluginWsTicketService.PARAM_NAME, issued.token());
        assertFalse(handshake(request, new HashMap<>()));
    }

    @Test
    @DisplayName("权限交集不放大：实时侧为 * 但票据快照受限 → 仍受限并拒绝")
    void intersectionNeverEscalates() {
        var issued = tickets.issue(7L, List.of("plugin:limited:view"), CODE, PATH);
        rebuildInterceptorWithFullLivePermissions();
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "http://127.0.0.1:8080");
        request.addParameter(PluginWsTicketService.PARAM_NAME, issued.token());
        assertFalse(handshake(request, new HashMap<>()));
    }

    private void rebuildInterceptorWithFullLivePermissions() {
        WsEndpointResolver resolver = (code, path) ->
                Optional.of(new PluginWsEndpointBinding(CODE, PATH, PERMISSION, handler));
        WsAuthSupport authSupport = new WsAuthSupport() {
            @Override
            public PluginPrincipal fromRequestContext(HttpServletRequest request,
                                                      Consumer<CredentialSource> credentialSource) {
                credentialSource.accept(CredentialSource.ANONYMOUS);
                return null;
            }

            @Override
            public List<String> permissions(Long userId) {
                return List.of("*");
            }
        };
        interceptor = new PluginWsHandshakeInterceptor(resolver, authSupport, tickets,
                new PluginWsOriginPolicy(List.of()));
    }

    @Test
    @DisplayName("身份不可复核（注销/未知用户）→ 拒绝")
    void unverifiableIdentityRejected() {
        var issued = tickets.issue(7L, List.of(PERMISSION), CODE, PATH);
        verifiedUserId = 999L;
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "http://127.0.0.1:8080");
        request.addParameter(PluginWsTicketService.PARAM_NAME, issued.token());
        assertFalse(handshake(request, new HashMap<>()));
    }

    @Test
    @DisplayName("header 凭据通道：无 Origin 放行；权限不足拒绝")
    void headerCredentialChannel() {
        contextPrincipal = new PluginPrincipal(7L, List.of(PERMISSION));
        assertTrue(handshake(request(), new HashMap<>()));

        contextPrincipal = new PluginPrincipal(7L, List.of("plugin:other:view"));
        assertFalse(handshake(request(), new HashMap<>()));
    }

    @Test
    @DisplayName("跨域白名单放行与拒绝")
    void originAllowList() {
        contextPrincipal = new PluginPrincipal(7L, List.of(PERMISSION));
        MockHttpServletRequest allowed = request();
        allowed.addHeader("Origin", "https://demo.example.com");
        assertTrue(handshake(allowed, new HashMap<>()));

        MockHttpServletRequest denied = request();
        denied.addHeader("Origin", "https://evil.example.com");
        assertFalse(handshake(denied, new HashMap<>()));
    }
}
