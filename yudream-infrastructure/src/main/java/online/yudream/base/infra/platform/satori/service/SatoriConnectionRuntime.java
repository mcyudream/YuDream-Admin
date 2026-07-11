package online.yudream.base.infra.platform.satori.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriEventCursorRepo;
import online.yudream.base.domain.platform.satori.service.SatoriEventGateway;
import online.yudream.base.domain.platform.satori.service.SatoriEventIngress;
import online.yudream.base.domain.platform.satori.service.SatoriOperationLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns every active socket and its heartbeat/reconnect subscription by connection id. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriConnectionRuntime implements SatoriEventGateway {
    private final ReactorSatoriEventGateway eventGateway;
    private final SatoriConnectionRepo connectionRepo;
    private final SatoriEventCursorRepo cursorRepo;
    private final SatoriEventIngress eventIngress;
    private final SatoriOperationLogger operationLogger;
    private final Map<Long, ConnectionResource> resources = new ConcurrentHashMap<>();

    @Override
    public void connect(Long connectionId) {
        close(connectionId);
        SatoriConnection connection = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new BizException("Satori 连接不存在"));
        if (!connection.enabled()) throw new BizException("Satori 连接未启用");
        String lastSequence = cursorRepo.findByConnectionId(connectionId).map(cursor -> cursor.getSequence()).orElse(null);
        ConnectionResource resource = new ConnectionResource();
        operationLogger.info(connectionId, "WEBSOCKET", "connect", "正在连接 Satori WebSocket");
        Disposable disposable = eventGateway.connect(connection, lastSequence, new ReactorSatoriEventGateway.SatoriSessionListener() {
            @Override public void onReady(java.util.List<online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriLogin> logins,
                                          online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta meta, Set<String> proxyUrls) {
                eventIngress.synchronizeLogins(connectionId, logins);
                resource.replaceProxyUrls(proxyUrls);
                eventIngress.acceptMeta(connectionId, meta);
                operationLogger.info(connectionId, "WEBSOCKET", "ready", "WebSocket 已就绪，登录账号数 " + logins.size());
            }
            @Override public void onMeta(online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta meta, Set<String> proxyUrls) {
                resource.replaceProxyUrls(proxyUrls);
                eventIngress.acceptMeta(connectionId, meta);
            }
            @Override public void onEvent(online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent event, String rawData) {
                eventIngress.acceptEvent(connectionId, event, rawData);
                operationLogger.info(connectionId, "EVENT", event.type(), eventSummary(event));
            }
        });
        resource.disposable = disposable;
        resources.put(connectionId, resource);
    }

    @Override
    public void close(Long connectionId) {
        ConnectionResource resource = resources.remove(connectionId);
        if (resource != null && resource.disposable != null) {
            resource.disposable.dispose();
            operationLogger.info(connectionId, "WEBSOCKET", "closed", "WebSocket 会话已关闭");
        }
    }

    @Override
    public void closeAll() {
        resources.keySet().forEach(this::close);
    }

    public Set<String> proxyUrls(Long connectionId) {
        ConnectionResource resource = resources.get(connectionId);
        return resource == null ? Set.of() : resource.proxyUrls();
    }

    @PreDestroy
    void destroy() { closeAll(); }

    private static final class ConnectionResource {
        private volatile Disposable disposable;
        private volatile Set<String> proxyUrls = Set.of();
        private void replaceProxyUrls(Set<String> values) { proxyUrls = values == null ? Set.of() : Set.copyOf(values); }
        private Set<String> proxyUrls() { return proxyUrls; }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreEnabledConnections() {
        for (SatoriConnection connection : connectionRepo.findEnabled()) {
            try {
                operationLogger.info(connection.getId(), "WEBSOCKET", "restore", "应用启动后恢复 WebSocket 会话");
                connect(connection.getId());
            } catch (RuntimeException exception) {
                operationLogger.error(connection.getId(), "WEBSOCKET", "restore", "恢复 WebSocket 会话失败: " + exception.getMessage());
            }
        }
    }

    private String eventSummary(online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent event) {
        String userId = event.user() != null ? event.user().id()
                : event.message() != null && event.message().user() != null ? event.message().user().id() : "-";
        String channelId = event.channel() == null ? "-" : event.channel().id();
        String content = event.message() == null ? "" : event.message().content();
        if (content != null) content = content.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (content != null && content.length() > 240) content = content.substring(0, 240) + "...";
        return "IN platform=" + event.platform() + " self=" + event.selfId() + " user=" + userId
                + " channel=" + channelId + " sn=" + event.sn() + (content == null || content.isBlank() ? "" : " content=" + content);
    }
}
