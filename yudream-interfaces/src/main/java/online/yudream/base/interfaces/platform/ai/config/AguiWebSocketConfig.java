package online.yudream.base.interfaces.platform.ai.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.ai.controller.AguiWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AguiWebSocketConfig implements WebSocketConfigurer {

    private final AguiWebSocketHandler aguiWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aguiWebSocketHandler, "/api/platform/ai/cms/pages/generate/ws")
                .setAllowedOriginPatterns("*");
    }
}
