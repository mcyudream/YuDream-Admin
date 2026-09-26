package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.ws.PluginWsCloseStatus;
import online.yudream.base.plugin.spi.ws.PluginWsHandler;
import online.yudream.base.plugin.spi.ws.PluginWsHandshake;
import online.yudream.base.plugin.spi.ws.PluginWsSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 桥接处理器：连接/消息分发、插件回调异常隔离、TOCTOU 代际复核、
 * 生命周期（disable/unload/reload/启用失败）强制关会话与 onClose 回调。
 */
class PluginWsBridgeLifecycleTest {

    private static final String CODE = "demo";
    private static final String PATH = "/chat";
    private static final PluginPrincipal PRINCIPAL = new PluginPrincipal(7L, List.of("p"));
    /** 直通 executor 桩：ExecutorService 非函数接口，需显式实现。 */
    private static java.util.concurrent.ExecutorService directExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                return java.util.List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
        };
    }

    private final List<String> opened = new CopyOnWriteArrayList<>();
    private final List<String> closed = new CopyOnWriteArrayList<>();
    private final AtomicInteger boom = new AtomicInteger();
    private final AtomicReference<PluginWsHandler> currentHandler = new AtomicReference<>();
    private PluginWsHandler handler;
    private PluginWsBridgeHandler bridge;

    @BeforeEach
    void setUp() {
        opened.clear();
        closed.clear();
        boom.set(0);
        handler = new PluginWsHandler() {
            @Override
            public void onOpen(PluginWsSession s, PluginWsHandshake h) {
                opened.add(h.path());
            }

            @Override
            public void onText(PluginWsSession s, String payload) {
                if (boom.get() > 0) {
                    throw new IllegalStateException("插件内部错误");
                }
                s.sendText("echo:" + payload);
            }

            @Override
            public void onClose(PluginWsSession s, int statusCode, String reason) {
                closed.add(statusCode + ":" + reason);
            }
        };
        currentHandler.set(handler);
        bridge = new PluginWsBridgeHandler(new PluginWsProperties(), (code, path) ->
                Optional.ofNullable(currentHandler.get())
                        .map(h -> new PluginWsEndpointBinding(CODE, PATH, "", h)), directExecutor());
    }

    private StubWebSocketSession newSession(PluginWsHandler boundHandler) {
        StubWebSocketSession session = new StubWebSocketSession();
        session.attributes.put(PluginWsAttributes.BINDING,
                new PluginWsEndpointBinding(CODE, PATH, "", boundHandler));
        session.attributes.put(PluginWsAttributes.PRINCIPAL, PRINCIPAL);
        session.attributes.put(PluginWsAttributes.PATH, PATH);
        session.attributes.put(PluginWsAttributes.QUERY, Map.of());
        session.attributes.put(PluginWsAttributes.HEADERS, Map.of());
        return session;
    }

    private PluginLifecycleEvent build(PluginLifecycleAction action, boolean success) {
        return new PluginLifecycleEvent(CODE, action, success, "1.0.0", 1L, null, java.time.Instant.now());
    }

    @Test
    @DisplayName("onOpen 回调携带握手；文本回显；正常关闭回调 onClose")
    void openTextCloseFlow() {
        StubWebSocketSession session = newSession(handler);
        bridge.afterConnectionEstablished(session);
        assertEquals(1, bridge.activeSessions());
        assertEquals(List.of(PATH), opened);

        bridge.handleTextMessage(session, new TextMessage("hi"));
        assertEquals(List.of("echo:hi"), session.sentTexts);

        bridge.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertEquals(List.of("1000:"), closed);
        assertEquals(0, bridge.activeSessions());
    }

    @Test
    @DisplayName("TOCTOU：插件 disable 后（resolver 返回空）禁止旧 handler onOpen 并以 1001 关闭")
    void toctouDisabledPluginRejected() {
        StubWebSocketSession session = newSession(handler);
        currentHandler.set(null);
        bridge.afterConnectionEstablished(session);
        assertTrue(opened.isEmpty(), "禁用后不得调用 onOpen");
        assertEquals(0, bridge.activeSessions());
        assertEquals(PluginWsCloseStatus.GOING_AWAY, session.closedWith.get(0).getCode());
    }

    @Test
    @DisplayName("TOCTOU：RELOAD 换代（resolver 返回新 handler 实例）旧会话不得 onOpen")
    void toctouReloadedGenerationRejected() {
        StubWebSocketSession session = newSession(handler);
        PluginWsHandler nextGeneration = new PluginWsHandler() {
            @Override
            public void onOpen(PluginWsSession s, PluginWsHandshake h) {
                opened.add("new");
            }
        };
        currentHandler.set(nextGeneration);
        bridge.afterConnectionEstablished(session);
        assertTrue(opened.isEmpty(), "换代后旧 handler 不得 onOpen");
        assertEquals(PluginWsCloseStatus.GOING_AWAY, session.closedWith.get(0).getCode());
    }

    @Test
    @DisplayName("TOCTOU：登记后生命周期先行关闭（会话已关闭）→ 不调用 onOpen 且清理注册")
    void toctouLifecycleRaceClosesBeforeOpen() {
        StubWebSocketSession session = newSession(handler);
        // 模拟握手后、建连处理前，插件 disable 已关闭容器会话
        session.close(CloseStatus.GOING_AWAY);
        bridge.afterConnectionEstablished(session);
        assertTrue(opened.isEmpty());
        assertEquals(0, bridge.activeSessions());
    }

    @Test
    @DisplayName("插件 onText 抛错 → 会话以 1011 关闭，不向容器外抛异常")
    void handlerExceptionClosesSession() {
        StubWebSocketSession session = newSession(handler);
        bridge.afterConnectionEstablished(session);
        boom.set(1);
        bridge.handleTextMessage(session, new TextMessage("hi"));
        assertEquals(1, session.closedWith.size());
        assertEquals(PluginWsCloseStatus.SERVER_ERROR, session.closedWith.get(0).getCode());
    }

    @Test
    @DisplayName("DISABLE/UNLOAD/RELOAD 成功强制关会话（1001）并回调 onClose；LOAD 不关")
    void lifecycleClosesPluginSessions() {
        StubWebSocketSession session = newSession(handler);
        bridge.afterConnectionEstablished(session);

        bridge.onPluginLifecycle(build(PluginLifecycleAction.LOAD, true));
        assertTrue(session.isOpen(), "LOAD 不应关闭会话");

        bridge.onPluginLifecycle(build(PluginLifecycleAction.DISABLE, true));
        assertTrue(!session.isOpen());
        assertEquals(PluginWsCloseStatus.GOING_AWAY, session.closedWith.get(0).getCode());
        bridge.afterConnectionClosed(session, new CloseStatus(PluginWsCloseStatus.GOING_AWAY, "插件已停用"));
        assertEquals(0, bridge.activeSessions());
        assertEquals(List.of(PluginWsCloseStatus.GOING_AWAY + ":插件已停用"), closed);
    }

    @Test
    @DisplayName("其他插件生命周期不影响本插件会话")
    void otherPluginNotAffected() {
        StubWebSocketSession session = newSession(handler);
        bridge.afterConnectionEstablished(session);
        bridge.onPluginLifecycle(new PluginLifecycleEvent(
                "other", PluginLifecycleAction.DISABLE, true, "1.0.0", 1L, null, java.time.Instant.now()));
        assertTrue(session.isOpen());
    }

    @Test
    @DisplayName("发送 deadline：写卡住的会话被 sweep 以 1011 关闭")
    void sendDeadlineSweepClosesStalledSession() throws Exception {
        StubWebSocketSession slow = new StubWebSocketSession() {
            @Override
            public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message)
                    throws java.io.IOException {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.sendMessage(message);
            }
        };
        // 复用 setUp 的 session 构造逻辑
        slow.attributes.put(PluginWsAttributes.BINDING,
                new PluginWsEndpointBinding(CODE, PATH, "", handler));
        slow.attributes.put(PluginWsAttributes.PRINCIPAL, PRINCIPAL);
        slow.attributes.put(PluginWsAttributes.PATH, PATH);
        slow.attributes.put(PluginWsAttributes.QUERY, Map.of());
        slow.attributes.put(PluginWsAttributes.HEADERS, Map.of());

        // 独立小超时配置 + 单线程 writer，使写卡住可复现
        PluginWsProperties props = new PluginWsProperties();
        props.setSendTimeLimitMs(50);
        PluginWsBridgeHandler timed = new PluginWsBridgeHandler(props, (code, path) ->
                Optional.of(new PluginWsEndpointBinding(CODE, PATH, "", handler)),
                Executors.newSingleThreadExecutor());
        timed.afterConnectionEstablished(slow);
        timed.handleTextMessage(slow, new TextMessage("slow"));
        long deadline = System.currentTimeMillis() + 5000;
        while (slow.isOpen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        timed.sweepSendDeadlines();
        assertEquals(1011, slow.closedWith.get(slow.closedWith.size() - 1).getCode());
    }
}
