package online.yudream.base.interfaces.platform.realtime.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 双向通信（系统工具层）。
 *
 * <p>与 SSE 同理：WebSocket 是平台基础设施而非可配置能力，端点常开、没有启停开关，
 * 不参与平台能力闸门；这里只保留会话表、并发上限与回声语义。
 */
@Slf4j
@Component
public class WebSocketConnectionHandler extends TextWebSocketHandler {

    /** 并发会话硬上限，防止连接洪泛。 */
    private static final int MAX_SESSIONS = 500;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 当前活跃会话数。 */
    public int sessions() {
        return sessions.size();
    }

    /** 向所有会话广播文本。 */
    public void broadcast(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
            catch (IOException e) {
                log.debug("WebSocket 广播失败：{}", session.getId(), e);
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (sessions.size() >= MAX_SESSIONS) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("WebSocket 会话数已达上限"));
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

    /** 应用关闭时结束残留会话，避免容器等待超时。 */
    @PreDestroy
    public void closeAll() {
        sessions.values().forEach(session -> {
            try {
                session.close(CloseStatus.SERVICE_RESTARTED.withReason("服务关闭"));
            }
            catch (IOException e) {
                log.debug("WebSocket 会话关闭失败：{}", session.getId(), e);
            }
        });
        sessions.clear();
    }
}
