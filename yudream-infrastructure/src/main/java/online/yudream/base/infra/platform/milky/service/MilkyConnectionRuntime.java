package online.yudream.base.infra.platform.milky.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyEventGateway;
import online.yudream.base.infra.platform.milky.official.OfficialQqBotEventGateway;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilkyConnectionRuntime implements MilkyEventGateway {
    private final ReactorMilkyEventGateway gateway;
    private final OfficialQqBotEventGateway officialGateway;
    private final MilkyConnectionRepo repo;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final SpringMilkyEventPublisher publisher;
    private final Map<Long, Disposable> sessions = new ConcurrentHashMap<>();

    @Override
    public void connect(Long id) {
        close(id);
        if (!capabilityEnabled()) {
            log.info("Milky connection skipped because capability is disabled: connectionId={}", id);
            return;
        }
        var connection = repo.findById(id).orElseThrow();
        if (!connection.isEnabled()) {
            log.info("Milky connection skipped because it is disabled: connectionId={}", id);
            return;
        }
        log.info("Starting messaging connection: connectionId={}, protocol={}", id, connection.protocolCode());
        Disposable session = connection.official()
                ? officialGateway.connect(connection, (event, raw) -> publisher.publish(id, event))
                : gateway.connect(connection, (event, raw) -> publisher.publish(id, event));
        sessions.put(id, session);
    }

    @Override
    public void close(Long id) {
        Disposable session = sessions.remove(id);
        if (session != null) {
            log.info("Closing messaging connection: connectionId={}", id);
            session.dispose();
        }
    }

    @Override
    public void closeAll() {
        sessions.keySet().forEach(this::close);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        if (!capabilityEnabled()) {
            log.info("Milky connection restore skipped because capability is disabled");
            return;
        }
        repo.findEnabled().forEach(connection -> {
            try {
                connect(connection.getId());
            } catch (Exception exception) {
                log.error("Milky connection restore failed: connectionId={}", connection.getId(), exception);
            }
        });
    }

    @EventListener
    public void shutdown(MilkyRuntimeShutdownRequested ignored) {
        closeAll();
    }

    @PreDestroy
    void stop() {
        closeAll();
    }

    private boolean capabilityEnabled() {
        return capabilityModuleRepo.findByCode("milky").map(CapabilityModule::enabled).orElse(false);
    }
}
