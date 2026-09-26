package online.yudream.base.interfaces.platform.plugin.ws;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.List;
import java.util.Map;

/**
 * 插件 WebSocket 桥接装配：自建 {@link SimpleUrlHandlerMapping}（order=-100，
 * 确定性优先于 MVC 注解映射），仅放行真实 WebSocket 升级请求，其余一律 404。
 * 不经 {@code @EnableWebSocket}，避免与既有平台 WS 配置耦合。
 */
@Configuration
@ConditionalOnProperty(prefix = "yudream.plugin.ws", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PluginWsProperties.class)
public class PluginWsMappingConfiguration {

    /**
     * 仅设置消息缓冲上限；不设置会话空闲超时——ServletServerContainerFactoryBean 作用于
     * 整个 ServletContext 共享的 WebSocket 容器，设置 idle 会侵入既有 AGUI 等平台 WS 行为。
     */
    @Bean
    public ServletServerContainerFactoryBean pluginWsServletContainer(PluginWsProperties properties) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxMessageBufferSizeBytes());
        container.setMaxBinaryMessageBufferSize(properties.getMaxMessageBufferSizeBytes());
        return container;
    }

    @Bean
    public PluginWsBridgeHandler pluginWsBridgeHandler(PluginWsProperties properties,
                                                       WsEndpointResolver endpointResolver) {
        return new PluginWsBridgeHandler(properties, endpointResolver);
    }

    @Bean
    public PluginWsHandshakeInterceptor pluginWsHandshakeInterceptor(WsEndpointResolver endpointResolver,
                                                                     WsAuthSupport authSupport,
                                                                     PluginWsTicketService ticketService,
                                                                     PluginWsProperties properties) {
        return new PluginWsHandshakeInterceptor(endpointResolver, authSupport, ticketService,
                new PluginWsOriginPolicy(properties.getAllowedOrigins()));
    }

    @Bean
    public HandlerMapping pluginWsHandlerMapping(PluginWsBridgeHandler bridge,
                                                 PluginWsHandshakeInterceptor interceptor) {
        WebSocketHttpRequestHandler wsHandler = new WebSocketHttpRequestHandler(bridge, new DefaultHandshakeHandler());
        wsHandler.setHandshakeInterceptors(List.of(interceptor));
        HttpRequestHandler upgradeGuard = (HttpServletRequest request, HttpServletResponse response) -> {
            String upgrade = request.getHeader(HttpHeaders.UPGRADE);
            if (upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim())) {
                wsHandler.handleRequest(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        };
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(
                Map.of(PluginWsPaths.WS_PATTERN, upgradeGuard), PluginWsPaths.MAPPING_ORDER);
        return mapping;
    }
}
