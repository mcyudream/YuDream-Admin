package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxAwarePluginDocumentStoreTest {

    @Test
    void overlaysWritesAndDeletesWithoutMutatingDelegate() {
        AtomicInteger writes = new AtomicInteger();
        PluginDocumentStore delegate = new PluginDocumentStore() {
            @Override public Map<String, Object> save(String collection, String id, Map<String, Object> document) { writes.incrementAndGet(); return document; }
            @Override public Optional<Map<String, Object>> findById(String collection, String id) { return Optional.of(Map.of("id", id, "value", "persisted")); }
            @Override public List<Map<String, Object>> findAll(String collection, int page, int size) { return List.of(Map.of("id", "1", "value", "persisted")); }
            @Override public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) { return List.of(); }
            @Override public long count(String collection) { return 1; }
            @Override public void delete(String collection, String id) { writes.incrementAndGet(); }
        };
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);
        QqSandboxSession session = QqSandboxSession.create("sandbox", "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 1_000L, Instant.now());

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            store.save("items", "1", Map.of("value", "overlay"));
            assertEquals("overlay", store.findById("items", "1").orElseThrow().get("value"));
            store.delete("items", "1");
            assertTrue(store.findById("items", "1").isEmpty());
            assertTrue(store.findAll("items", 1, 20).isEmpty());
        }

        assertEquals(0, writes.get());
    }
}
