package online.yudream.base.infra.platform.satori.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.satori.aggregate.SatoriOperationLog;
import online.yudream.base.domain.platform.satori.repo.SatoriOperationLogRepo;
import online.yudream.base.domain.platform.satori.service.SatoriOperationLogger;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistentSatoriOperationLogger implements SatoriOperationLogger {
    private final SatoriOperationLogRepo repo;

    @Override public void info(Long connectionId, String category, String action, String detail) { save(connectionId, "INFO", category, action, detail); }
    @Override public void warn(Long connectionId, String category, String action, String detail) { save(connectionId, "WARN", category, action, detail); }
    @Override public void error(Long connectionId, String category, String action, String detail) { save(connectionId, "ERROR", category, action, detail); }

    private void save(Long connectionId, String level, String category, String action, String detail) {
        if (connectionId == null) return;
        try {
            repo.save(SatoriOperationLog.create(connectionId, level, category, action, detail));
        } catch (RuntimeException exception) {
            log.warn("Unable to persist Satori operation log: category={}, action={}", category, action, exception);
        }
    }
}
