package online.yudream.base.interfaces.platform.capability.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.capability.service.WebSocketCapabilityProvider;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.websocket", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WebSocketCapabilityConfig implements WebSocketConfigurer {

    private final WebSocketCapabilityProvider webSocketCapabilityProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketCapabilityProvider, "/api/platform/ws")
                .addInterceptors(new WebSocketCapabilityAuthInterceptor())
                .setAllowedOriginPatterns("*");
    }

    /**
     * 握手认证：浏览器 WebSocket 无法携带 Authorization 头，前端经查询参数传入登录令牌
     * （与 AG-UI 端点一致）；令牌缺失或无效一律拒绝握手。
     */
    static class WebSocketCapabilityAuthInterceptor implements org.springframework.web.socket.server.HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                       @NonNull ServerHttpResponse response,
                                       @NonNull WebSocketHandler wsHandler,
                                       @NonNull Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String token = servletRequest.getServletRequest().getParameter("token");
                if (token == null || token.isBlank()) {
                    String header = servletRequest.getServletRequest().getHeader("Authorization");
                    token = header == null || header.isBlank() ? null : header.trim();
                }
                try {
                    if (token != null && SecurityPrincipalSupport.fromToken(token) != null) {
                        attributes.put("ws.principal.token", token);
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // 令牌无效按未认证处理
                }
            }
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        @Override
        public void afterHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   Exception exception) {
        }
    }
}
