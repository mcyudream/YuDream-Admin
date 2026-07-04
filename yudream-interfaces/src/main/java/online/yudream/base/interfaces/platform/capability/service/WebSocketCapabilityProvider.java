package online.yudream.base.interfaces.platform.capability.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.websocket", name = "enabled", havingValue = "true")
public class WebSocketCapabilityProvider extends TextWebSocketHandler implements CapabilityProvider {

    public static final String CODE = "websocket";

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "WebSocket 双向通信",
                CapabilityType.REALTIME,
                "提供浏览器 WebSocket 双向实时通信能力",
                "i-ri:exchange-line",
                90,
                Map.of("endpoint", "/api/platform/ws")
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("WebSocket 未启用");
        }
        return CapabilityHealth.enabled("WebSocket 运行中", Map.of("sessions", sessions.size()));
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
        sessions.values().forEach(session -> {
            try {
                session.close(CloseStatus.SERVICE_RESTARTED.withReason("WebSocket 能力已停用"));
            } catch (IOException ignored) {
            }
        });
        sessions.clear();
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("WebSocket 未启用");
        }
        broadcast("[capability-test] " + message);
        return CapabilityTestResult.success("WebSocket 测试消息已推送，会话数：" + sessions.size());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!enabled.get()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("WebSocket 能力未启用"));
            return;
        }
        sessions.put(session.getId(), session);
        session.sendMessage(new TextMessage("WebSocket 已连接"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(new TextMessage("echo: " + message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    private void broadcast(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException ignored) {
            }
        });
    }
}
