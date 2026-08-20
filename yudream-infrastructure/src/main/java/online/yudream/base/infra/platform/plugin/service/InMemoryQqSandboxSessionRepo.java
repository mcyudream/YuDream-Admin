package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryQqSandboxSessionRepo implements QqSandboxSessionRepo {
    static final Duration IDLE_TTL = Duration.ofMinutes(30);
    static final int DEFAULT_MAX_SESSIONS = 200;

    private final ConcurrentMap<String, QqSandboxSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxSessions;

    public InMemoryQqSandboxSessionRepo() {
        this(Clock.systemUTC(), DEFAULT_MAX_SESSIONS);
    }

    InMemoryQqSandboxSessionRepo(Clock clock, int maxSessions) {
        this.clock = clock;
        this.maxSessions = Math.max(1, maxSessions);
    }

    @Override
    public synchronized void save(QqSandboxSession session) {
        cleanup();
        session.touch(clock.instant());
        sessions.put(session.id(), session);
        enforceBound();
    }

    @Override
    public synchronized Optional<QqSandboxSession> findById(String id) {
        cleanup();
        QqSandboxSession session = sessions.get(id);
        if (session != null) session.touch(clock.instant());
        return Optional.ofNullable(session);
    }

    @Override
    public synchronized Optional<QqSandboxSession> findByConnectionId(String connectionId) {
        cleanup();
        if (connectionId == null || !connectionId.startsWith("devtools-sandbox:")) return Optional.empty();
        QqSandboxSession session = sessions.get(connectionId.substring("devtools-sandbox:".length()));
        if (session != null) session.touch(clock.instant());
        return Optional.ofNullable(session);
    }

    @Override
    public synchronized List<QqSandboxSession> findAll() {
        cleanup();
        return sessions.values().stream().sorted(Comparator.comparing(QqSandboxSession::lastAccessAt).reversed()).toList();
    }

    @Override
    public synchronized void delete(String id) {
        cleanup();
        close(sessions.remove(id));
    }

    synchronized int size() {
        cleanup();
        return sessions.size();
    }

    private void cleanup() {
        Instant cutoff = clock.instant().minus(IDLE_TTL);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().lastAccessAt().isAfter(cutoff)) return false;
            close(entry.getValue());
            return true;
        });
    }

    private void enforceBound() {
        while (sessions.size() > maxSessions) {
            QqSandboxSession oldest = sessions.values().stream()
                    .min(Comparator.comparing(QqSandboxSession::lastAccessAt)).orElse(null);
            if (oldest == null || !sessions.remove(oldest.id(), oldest)) return;
            close(oldest);
        }
    }

    private void close(QqSandboxSession session) {
        if (session != null) QqSandboxExecutionScope.cancelPending(session);
    }
}
