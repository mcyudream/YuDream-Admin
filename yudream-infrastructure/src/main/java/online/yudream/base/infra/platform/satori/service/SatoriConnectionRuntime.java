package online.yudream.base.infra.platform.satori.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriEventCursorRepo;
import online.yudream.base.domain.platform.satori.service.SatoriEventGateway;
import online.yudream.base.domain.platform.satori.service.SatoriEventIngress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final Map<Long, ConnectionResource> resources = new ConcurrentHashMap<>();

    @Override
    public void connect(Long connectionId) {
        close(connectionId);
        SatoriConnection connection = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new BizException("Satori 连接不存在"));
        if (!connection.enabled()) throw new BizException("Satori 连接未启用");
        String lastSequence = cursorRepo.findByConnectionId(connectionId).map(cursor -> cursor.getSequence()).orElse(null);
        ConnectionResource resource = new ConnectionResource();
        Disposable disposable = eventGateway.connect(connection, lastSequence, new ReactorSatoriEventGateway.SatoriSessionListener() {
            @Override public void onReady(java.util.List<online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriLogin> logins,
                                          online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta meta, Set<String> proxyUrls) {
                eventIngress.synchronizeLogins(connectionId, logins);
                resource.replaceProxyUrls(proxyUrls);
                eventIngress.acceptMeta(connectionId, meta);
            }
            @Override public void onMeta(online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta meta, Set<String> proxyUrls) {
                resource.replaceProxyUrls(proxyUrls);
                eventIngress.acceptMeta(connectionId, meta);
            }
            @Override public void onEvent(online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent event, String rawData) {
                eventIngress.acceptEvent(connectionId, event, rawData);
            }
        });
        resource.disposable = disposable;
        resources.put(connectionId, resource);
    }

    @Override
    public void close(Long connectionId) {
        ConnectionResource resource = resources.remove(connectionId);
        if (resource != null && resource.disposable != null) resource.disposable.dispose();
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
}
