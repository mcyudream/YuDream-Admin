package online.yudream.base.application.platform.milky.sandbox.service;

import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxCreateCmd;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.application.platform.milky.sandbox.port.QqSandboxRuntimeGateway;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqSandboxAppServiceTest {

    @Test
    void createsHostConnectionAndPropagatesCompleteSyntheticMessage() {
        MemoryRepo repo = new MemoryRepo();
        AtomicReference<QqSandboxMessageCmd> dispatched = new AtomicReference<>();
        QqSandboxRuntimeGateway runtime = (session, message) -> {
            dispatched.set(message);
            session.append("normalized", "plugin.event", session.pluginCode(), Map.of("content", message.content()));
            return CompletableFuture.completedFuture(null);
        };
        QqSandboxAppService service = new QqSandboxAppService(repo, runtime, null, null, null);
        var created = service.create(new QqSandboxCreateCmd("demo", "1", "456", "789", "Tester", "999", "group",
                QqSandboxRandomMode.FORCE_HIT, false, null, 1_000L));
        QqSandboxMessageCmd message = new QqSandboxMessageCmd("789", "Tester", "/hello world", true,
                List.of("111", "222"), "9007199254740995", "client-1");

        var result = service.send(created.id(), message);

        assertEquals("devtools-sandbox:" + created.id(), result.connectionId());
        assertEquals("Tester", created.nickname());
        assertEquals(QqSandboxRandomMode.FORCE_HIT, result.randomMode());
        assertEquals(message, dispatched.get());
        assertTrue(result.timeline().stream().anyMatch(event -> "plugin.event".equals(event.action())));
    }

    @Test
    void marksTimedOutDispatchAndReturnsSessionSnapshot() {
        MemoryRepo repo = new MemoryRepo();
        QqSandboxAppService service = new QqSandboxAppService(repo, (session, message) -> new CompletableFuture<>(), null, null, null);
        var created = service.create(new QqSandboxCreateCmd("demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.FORCE_MISS, false, null, 1L));

        var result = service.send(created.id(), new QqSandboxMessageCmd("3", "Tester", "/slow", false,
                List.of(), null, "client-2"));

        assertEquals("TIMED_OUT", result.status());
        assertTrue(result.timeline().stream().anyMatch(event -> "dispatch.timeout".equals(event.action())));
    }

    @Test
    void rejectsUnknownSession() {
        QqSandboxAppService service = new QqSandboxAppService(new MemoryRepo(),
                (session, message) -> CompletableFuture.completedFuture(null), null, null, null);

        assertThrows(RuntimeException.class, () -> service.send("missing",
                new QqSandboxMessageCmd("3", null, "/hello", false, List.of(), null, null)));
    }

    @Test
    void validatesPolicyConnectionExistsAndIsEnabled() {
        QqSandboxAppService missing = serviceWithConnection(Optional.empty());
        assertThrows(RuntimeException.class, () -> missing.create(createCmd()));

        MilkyConnection disabled = connection(1L);
        disabled.setEnabled(false);
        QqSandboxAppService disabledService = serviceWithConnection(Optional.of(disabled));
        assertThrows(RuntimeException.class, () -> disabledService.create(createCmd()));

        QqSandboxAppService enabled = serviceWithConnection(Optional.of(connection(1L)));
        assertEquals("1", enabled.create(createCmd()).policyConnectionId());
    }

    private QqSandboxAppService serviceWithConnection(Optional<MilkyConnection> connection) {
        QqSandboxAppService service = new QqSandboxAppService(new MemoryRepo(),
                (session, message) -> CompletableFuture.completedFuture(null), null, null, null);
        MilkyConnectionRepo repo = (MilkyConnectionRepo) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{MilkyConnectionRepo.class},
                (proxy, method, args) -> "findById".equals(method.getName()) ? connection : null);
        service.setMilkyConnections(repo);
        return service;
    }

    private QqSandboxCreateCmd createCmd() {
        return new QqSandboxCreateCmd("demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, false, null, 1_000L);
    }

    private MilkyConnection connection(Long id) {
        MilkyConnection connection = MilkyConnection.create("policy", "http://localhost", "token", "base64", null);
        connection.setId(id);
        return connection;
    }

    private static final class MemoryRepo implements QqSandboxSessionRepo {
        private final Map<String, QqSandboxSession> values = new ConcurrentHashMap<>();
        @Override public void save(QqSandboxSession session) { values.put(session.id(), session); }
        @Override public Optional<QqSandboxSession> findById(String id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<QqSandboxSession> findByConnectionId(String connectionId) {
            return values.values().stream().filter(session -> session.connectionId().equals(connectionId)).findFirst();
        }
        @Override public java.util.List<QqSandboxSession> findAll() { return java.util.List.copyOf(values.values()); }
        @Override public void delete(String id) { values.remove(id); }
    }
}
