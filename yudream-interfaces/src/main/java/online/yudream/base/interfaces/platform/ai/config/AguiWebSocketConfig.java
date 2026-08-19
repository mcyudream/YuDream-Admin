package online.yudream.base.interfaces.platform.ai.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.ai.controller.AguiWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

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

    /**
     * 画布纲要和组件详情会作为 TOOL_RESULT 文本帧回传；容器默认约 8KB，会以 1009 关闭合法的大结果帧。
     * 仍由前端工具限制节点/HTML 规模，这里只为受控的结构化结果保留足够缓冲。
     */
    @Bean
    public ServletServerContainerFactoryBean aguiWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(1024 * 1024);
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        return container;
    }
}
