package online.yudream.base.interfaces.platform.realtime.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.realtime.service.WebSocketConnectionHandler;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 端点注册（系统工具层，常开）。
 *
 * <p>原先由平台能力 websocket 的启停开关控制是否注册，现改为系统级基础设施直接注册。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketConnectionHandler webSocketConnectionHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketConnectionHandler, "/api/platform/ws")
                .addInterceptors(new WebSocketAuthInterceptor())
                .setAllowedOriginPatterns("*");
    }

    /**
     * 握手认证：浏览器 WebSocket 无法携带 Authorization 头，前端经查询参数传入登录令牌
     * （与 AG-UI 端点一致）；令牌缺失或无效一律拒绝握手。
     */
    static class WebSocketAuthInterceptor implements HandshakeInterceptor {

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
                }
                catch (RuntimeException ignored) {
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
