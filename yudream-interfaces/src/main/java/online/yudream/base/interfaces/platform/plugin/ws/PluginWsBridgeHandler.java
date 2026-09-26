package online.yudream.base.interfaces.platform.plugin.ws;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.ws.PluginWsCloseStatus;
import online.yudream.base.plugin.spi.ws.PluginWsHandshake;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 插件 WebSocket 桥接处理器：把容器会话适配为 SPI 会话并分发到插件处理器。
 *
 * <p>关键语义：
 * <ul>
 *   <li><b>TOCTOU 封闭</b>：握手与建连之间插件可能被 disable/reload。建连后先登记会话，
 *       再经 resolver 复核“插件仍启用且解析到同一 handler 实例”（注册表在 disable 时清空、
 *       重新启用产生新实例，实例同一性即代际一致性），复核失败或会话已被生命周期事件关闭
 *       时禁止旧 handler 的 onOpen 并以 1001 关闭；登记先于复核使生命周期关闭路径与建连
 *       路径互斥收敛于同一注册表，不存在“check 后 add”的窗口。</li>
 *   <li><b>生命周期联动</b>：DISABLE/UNLOAD/RELOAD 成功、ENABLE/RELOAD 失败时强制关闭
 *       该插件全部会话（dev-mode RELOAD 会更换类加载器），并回调 onClose。</li>
 *   <li><b>发送边界</b>：共享受控 writer 池承载每会话串行写出（插件线程仅入队），
 *       watchdog 周期扫描写出超 deadline 的会话并强制关闭。</li>
 * </ul>
 */
@Slf4j
public class PluginWsBridgeHandler extends AbstractWebSocketHandler {

    private static final long SEND_DEADLINE_SWEEP_MS = 1000;

    private final Map<String, PluginWsConnection> sessionsByConnectionId = new ConcurrentHashMap<>();
    private final PluginWsProperties properties;
    private final WsEndpointResolver endpointResolver;
    private final ExecutorService sendWriters;
    private final ExecutorService ownedWriterPool;
    private final ExecutorService sendWatchdog;

    public PluginWsBridgeHandler(PluginWsProperties properties, WsEndpointResolver endpointResolver) {
        this(properties, endpointResolver, null);
    }

    /** 测试注入直通/受控 executor 用；传 null 则内部创建默认池（容器关闭时一并释放）。 */
    public PluginWsBridgeHandler(PluginWsProperties properties, WsEndpointResolver endpointResolver,
                                 ExecutorService sendWriters) {
        this.properties = properties;
        this.endpointResolver = endpointResolver;
        if (sendWriters == null) {
            int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
            this.ownedWriterPool = Executors.newFixedThreadPool(threads, daemonFactory("plugin-ws-writer"));
            this.sendWriters = this.ownedWriterPool;
        } else {
            this.ownedWriterPool = null;
            this.sendWriters = sendWriters;
        }
        this.sendWatchdog = Executors.newSingleThreadScheduledExecutor(daemonFactory("plugin-ws-watchdog"));
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** 活跃会话数（运维/测试观察用）。 */
    public int activeSessions() {
        return sessionsByConnectionId.size();
    }

    /** watchdog 周期任务：关闭写出超 deadline 的会话。 */
    public void sweepSendDeadlines() {
        long now = System.currentTimeMillis();
        sessionsByConnectionId.values().forEach(connection -> connection.adapter().enforceSendDeadline(now));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        dispatch(session, connection -> connection.handler().onText(connection.session(), message.getPayload()));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer buffer = message.getPayload();
        // 防御性拷贝：不把容器缓冲的生命周期交给插件
        byte[] copy = new byte[buffer.remaining()];
        buffer.get(copy);
        dispatch(session, connection -> connection.handler().onBinary(connection.session(), copy));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        PluginWsConnection connection = sessionsByConnectionId.get(session.getId());
        if (connection != null) {
            try {
                connection.handler().onError(connection.session(), exception);
            } catch (Throwable t) {
                log.warn("插件 WS onError 回调异常：code={}", connection.pluginCode(), t);
            }
        }
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
            // 容器可能已关闭
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        PluginWsEndpointBinding binding = (PluginWsEndpointBinding) attributes.get(PluginWsAttributes.BINDING);
        PluginPrincipal principal = (PluginPrincipal) attributes.get(PluginWsAttributes.PRINCIPAL);
        String path = (String) attributes.get(PluginWsAttributes.PATH);
        if (binding == null || principal == null || path == null) {
            log.warn("插件 WS 会话缺少握手属性，关闭：sessionId={}", session.getId());
            closeQuietly(session, CloseStatus.SERVER_ERROR);
            return;
        }
        PluginWsSessionAdapter adapter = new PluginWsSessionAdapter(
                binding.pluginCode(), principal, session,
                properties.getSendTimeLimitMs(), properties.getSendBufferLimitBytes(), sendWriters);
        // 1) 先登记：生命周期关闭与建连复核读写同一注册表，无 check-then-add 窗口
        sessionsByConnectionId.put(session.getId(), new PluginWsConnection(binding, adapter));
        // 2) 代际复核：插件必须仍启用且解析到同一 handler 实例（disable 清空注册表、
        //    重新启用产生新实例；RELOAD 换类加载器同样产生新实例）
        Optional<PluginWsEndpointBinding> current = endpointResolver.resolve(binding.pluginCode(), binding.path());
        if (current.isEmpty() || current.get().handler() != binding.handler() || !session.isOpen()) {
            sessionsByConnectionId.remove(session.getId());
            log.info("插件 WS 建连复核失败（插件已停用或已更新）：code={}", binding.pluginCode());
            closeQuietly(session, new CloseStatus(PluginWsCloseStatus.GOING_AWAY, "插件已停用或已更新"));
            return;
        }
        // 3) 生命周期事件可能恰好在复核后关闭会话：isOpen 再确认后才允许 onOpen
        if (!session.isOpen()) {
            sessionsByConnectionId.remove(session.getId());
            return;
        }
        try {
            binding.handler().onOpen(adapter, new PluginWsHandshake(
                    path,
                    (Map<String, List<String>>) attributes.get(PluginWsAttributes.HEADERS),
                    (Map<String, List<String>>) attributes.get(PluginWsAttributes.QUERY),
                    principal));
        } catch (Throwable t) {
            log.warn("插件 WS onOpen 回调异常：code={}", binding.pluginCode(), t);
            sessionsByConnectionId.remove(session.getId());
            closeQuietly(session, new CloseStatus(PluginWsCloseStatus.SERVER_ERROR, "插件处理连接失败"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        PluginWsConnection connection = sessionsByConnectionId.remove(session.getId());
        if (connection == null) {
            return;
        }
        try {
            connection.handler().onClose(connection.session(), sanitize(status), reasonOrEmpty(status));
        } catch (Throwable t) {
            log.warn("插件 WS onClose 回调异常：code={}", connection.pluginCode(), t);
        }
    }

    /**
     * 插件生命周期联动：DISABLE/UNLOAD/RELOAD 成功、ENABLE/RELOAD 失败时
     * 强制关闭该插件全部会话。仅触发关闭，注册表清理与 onClose 回调统一走
     * afterConnectionClosed。
     */
    @EventListener
    public void onPluginLifecycle(PluginLifecycleEvent event) {
        PluginLifecycleAction action = event.action();
        boolean close = event.success()
                ? action == PluginLifecycleAction.DISABLE || action == PluginLifecycleAction.UNLOAD
                || action == PluginLifecycleAction.RELOAD
                : action == PluginLifecycleAction.ENABLE || action == PluginLifecycleAction.RELOAD;
        if (!close) {
            return;
        }
        String reason = event.success() ? "插件已停用" : "插件状态变更";
        sessionsByConnectionId.values().stream()
                .filter(connection -> connection.pluginCode().equals(event.pluginCode()))
                .forEach(connection -> connection.adapter().closeQuietly(PluginWsCloseStatus.GOING_AWAY, reason));
    }

    private interface HandlerCall {
        void invoke(PluginWsConnection connection);
    }

    private void dispatch(WebSocketSession session, HandlerCall call) {
        PluginWsConnection connection = sessionsByConnectionId.get(session.getId());
        if (connection == null) {
            return;
        }
        try {
            call.invoke(connection);
        } catch (Throwable t) {
            log.warn("插件 WS 消息回调异常：code={}", connection.pluginCode(), t);
            connection.adapter().closeQuietly(PluginWsCloseStatus.SERVER_ERROR, "插件处理失败");
        }
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // 已关闭
        }
    }

    private static int sanitize(CloseStatus status) {
        return status == null || status.getCode() < 1000 ? PluginWsCloseStatus.SERVER_ERROR : status.getCode();
    }

    private static String reasonOrEmpty(CloseStatus status) {
        return status == null || status.getReason() == null ? "" : status.getReason();
    }

    /** 容器关闭时释放线程池；外部注入的 writer 池不代管。 */
    @PreDestroy
    public void shutdown() {
        sendWatchdog.shutdownNow();
        if (ownedWriterPool != null) {
            ownedWriterPool.shutdownNow();
        }
    }
}
