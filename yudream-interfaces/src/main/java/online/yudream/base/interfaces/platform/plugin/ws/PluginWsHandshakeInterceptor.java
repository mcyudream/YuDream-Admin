package online.yudream.base.interfaces.platform.plugin.ws;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 握手拦截器：路径解析 → 端点解析（插件必须启用且路由仍在）→ 凭据解析 →
 * Origin 严格校验 → 票据消费（强绑定 code+path、单次、实时权限交集复核）→ 权限判定。
 *
 * <p>日志只包含插件 code 与路径，绝不含票据 token 与查询串。
 */
@Slf4j
public class PluginWsHandshakeInterceptor implements HandshakeInterceptor {

    private final WsEndpointResolver endpointResolver;
    private final WsAuthSupport authSupport;
    private final PluginWsTicketService ticketService;
    private final PluginWsOriginPolicy originPolicy;

    public PluginWsHandshakeInterceptor(WsEndpointResolver endpointResolver,
                                        WsAuthSupport authSupport,
                                        PluginWsTicketService ticketService,
                                        PluginWsOriginPolicy originPolicy) {
        this.endpointResolver = endpointResolver;
        this.authSupport = authSupport;
        this.ticketService = ticketService;
        this.originPolicy = originPolicy;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return reject(response, HttpStatus.BAD_REQUEST);
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        WsRoute route = parseRoute(httpRequest);
        if (route == null) {
            return reject(response, HttpStatus.NOT_FOUND);
        }
        var binding = endpointResolver.resolve(route.code(), route.path());
        if (binding.isEmpty()) {
            // 插件不存在/未启用/路由未注册统一 404，不区分泄露信息
            log.info("插件 WS 握手拒绝：code={}, path={}, reason=endpoint-unavailable", route.code(), route.path());
            return reject(response, HttpStatus.NOT_FOUND);
        }

        WsAuthSupport.CredentialSource[] source = {WsAuthSupport.CredentialSource.ANONYMOUS};
        PluginPrincipal contextPrincipal = authSupport.fromRequestContext(httpRequest, s -> source[0] = s);

        // Origin 校验先于票据消费：同源/白名单不满足时不烧票据。Origin 缺失仅 header 凭据通道放行。
        if (!originPolicy.allows(httpRequest.getHeader("Origin"), requestBaseUri(httpRequest),
                source[0] == WsAuthSupport.CredentialSource.AUTHORIZATION_CONTEXT)) {
            log.info("插件 WS 握手拒绝：code={}, path={}, reason=origin-denied", route.code(), route.path());
            return reject(response, HttpStatus.FORBIDDEN);
        }

        PluginPrincipal principal;
        if (contextPrincipal != null) {
            principal = contextPrincipal;
        } else {
            var consumed = ticketService.consume(
                    httpRequest.getParameter(PluginWsTicketService.PARAM_NAME), route.code(), route.path());
            if (consumed.isPresent()) {
                source[0] = WsAuthSupport.CredentialSource.TICKET;
                // 撤销/注销/禁用复查：按 userId 实时查权限；返回 null 表示身份已不可复核，拒绝握手。
                List<String> livePermissions = authSupport.permissions(consumed.get().userId());
                if (livePermissions == null) {
                    log.info("插件 WS 握手拒绝：code={}, path={}, reason=identity-unverifiable", route.code(), route.path());
                    return reject(response, HttpStatus.UNAUTHORIZED);
                }
                // 权限不可扩张：发放快照 ∩ 实时权限，防止受限 OAuth/API Key 主体被升级为用户全量权限
                principal = new PluginPrincipal(consumed.get().userId(),
                        intersectPermissions(consumed.get().permissions(), livePermissions));
            } else {
                principal = new PluginPrincipal(null, List.of());
            }
        }

        String permission = binding.get().permission();
        if (StringUtils.hasText(permission)) {
            if (principal.userId() == null) {
                return reject(response, HttpStatus.UNAUTHORIZED);
            }
            if (!principal.hasPermission(permission)) {
                log.info("插件 WS 握手拒绝：code={}, path={}, reason=permission-denied", route.code(), route.path());
                return reject(response, HttpStatus.FORBIDDEN);
            }
        }

        attributes.put(PluginWsAttributes.BINDING, binding.get());
        attributes.put(PluginWsAttributes.PRINCIPAL, principal);
        attributes.put(PluginWsAttributes.PATH, route.path());
        attributes.put(PluginWsAttributes.QUERY, query(httpRequest));
        attributes.put(PluginWsAttributes.HEADERS, headers(httpRequest));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    /** 发放快照与实时权限取交集（支持 * 通配，任一侧为 * 则取另一侧），绝不放大受限凭据。 */
    static List<String> intersectPermissions(List<String> issued, List<String> live) {
        List<String> safeIssued = issued == null ? List.of() : issued;
        List<String> safeLive = live == null ? List.of() : live;
        if (safeIssued.contains("*")) {
            return List.copyOf(safeLive);
        }
        if (safeLive.contains("*")) {
            return List.copyOf(safeIssued);
        }
        List<String> result = new ArrayList<>(safeIssued);
        result.retainAll(safeLive);
        return List.copyOf(result);
    }

    private static URI requestBaseUri(HttpServletRequest request) {
        try {
            int port = request.getServerPort();
            StringBuilder builder = new StringBuilder()
                    .append(request.getScheme()).append("://").append(request.getServerName());
            if (port > 0) {
                builder.append(':').append(port);
            }
            builder.append('/');
            return URI.create(builder.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** /api/platform/plugin-ws/{code}/{pluginPath...} → (code, path)；path 以 / 开头，缺失为 /。 */
    static WsRoute parseRoute(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (!uri.startsWith(PluginWsPaths.WS_PREFIX)) {
            return null;
        }
        String rest = uri.substring(PluginWsPaths.WS_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return StringUtils.hasText(rest) ? new WsRoute(rest, "/") : null;
        }
        String code = rest.substring(0, slash);
        String sub = rest.substring(slash);
        return StringUtils.hasText(code) ? new WsRoute(code, sub) : null;
    }

    record WsRoute(String code, String path) {
    }

    /** sa-token 会话令牌的查询参数名（token-name），同样不得透传给插件。 */
    private static volatile String cachedTokenName;

    private static String saTokenQueryName() {
        String cached = cachedTokenName;
        if (cached != null) {
            return cached;
        }
        String tokenName;
        try {
            tokenName = cn.dev33.satoken.SaManager.getConfig().getTokenName();
        } catch (RuntimeException e) {
            tokenName = "Authorization";
        }
        cachedTokenName = tokenName == null || tokenName.isBlank() ? "Authorization" : tokenName;
        return cachedTokenName;
    }

    private static Map<String, List<String>> query(HttpServletRequest request) {
        String tokenName = saTokenQueryName();
        Map<String, List<String>> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            // 票据与会话令牌不透传给插件：仅宿主握手消费。
            // 同时剥离框架默认名 "Authorization" 与运行时配置名：测试/未初始化环境下
            // sa-token 默认 token-name 为 "satoken"，不能因配置不同放过任一会话令牌参数。
            if (PluginWsTicketService.PARAM_NAME.equals(name)
                    || "Authorization".equals(name) || tokenName.equals(name)) {
                return;
            }
            result.put(name, values == null ? List.of() : List.of(values));
        });
        return Collections.unmodifiableMap(result);
    }

    /** 不得透传给插件的敏感头（凭证类）。 */
    private static final List<String> REDACTED_HEADERS = List.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name != null && REDACTED_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            result.put(name, Collections.list(request.getHeaders(name)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
