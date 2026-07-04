package online.yudream.base.interfaces.platform.capability.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.capability.service.WebSocketCapabilityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.websocket", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WebSocketCapabilityConfig implements WebSocketConfigurer {

    private final WebSocketCapabilityProvider webSocketCapabilityProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketCapabilityProvider, "/api/platform/ws")
                .setAllowedOriginPatterns("*");
    }
}
