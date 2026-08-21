package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxAwarePluginDocumentStoreTest {

    /** 模拟 Mongo 行为的真实分页内存实现 */
    private static final class InMemoryDelegate implements PluginDocumentStore {
        private final Map<String, Map<String, Map<String, Object>>> data = new LinkedHashMap<>();

        @Override
        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            Map<String, Object> value = new LinkedHashMap<>(document);
            value.put("id", id);
            data.computeIfAbsent(collection, ignored -> new LinkedHashMap<>()).put(id, value);
            return value;
        }

        @Override
        public Optional<Map<String, Object>> findById(String collection, String id) {
            return Optional.ofNullable(data.getOrDefault(collection, Map.of()).get(id));
        }

        @Override
        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            List<Map<String, Object>> all = new ArrayList<>(data.getOrDefault(collection, Map.of()).values());
            int from = Math.min((Math.max(page, 1) - 1) * Math.max(size, 1), all.size());
            int to = Math.min(from + Math.max(size, 1), all.size());
            return all.subList(from, to);
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
            List<Map<String, Object>> matches = data.getOrDefault(collection, Map.of()).values().stream()
                    .filter(item -> java.util.Objects.equals(item.get(field), value))
                    .toList();
            int from = Math.min((Math.max(page, 1) - 1) * Math.max(size, 1), matches.size());
            int to = Math.min(from + Math.max(size, 1), matches.size());
            return matches.subList(from, to);
        }

        @Override
        public long count(String collection) {
            return data.getOrDefault(collection, Map.of()).size();
        }

        @Override
        public void delete(String collection, String id) {
            data.getOrDefault(collection, Map.of()).remove(id);
        }
    }

    private QqSandboxSession session() {
        return QqSandboxSession.create("sandbox", "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 60_000L, Instant.now());
    }

    @Test
    void overlaysWritesAndDeletesWithoutMutatingDelegate() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        delegate.save("items", "1", Map.of("value", "persisted"));
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            store.save("items", "1", Map.of("value", "overlay"));
            assertEquals("overlay", store.findById("items", "1").orElseThrow().get("value"));
            store.delete("items", "1");
            assertTrue(store.findById("items", "1").isEmpty());
            assertTrue(store.findAll("items", 1, 20).isEmpty());
        }

        assertEquals("persisted", delegate.findById("items", "1").orElseThrow().get("value"));
    }

    @Test
    void delegatesFieldQueryAndCountDirectlyWhenNoSandbox() {
        AtomicBoolean fieldQueryUsed = new AtomicBoolean();
        AtomicBoolean countUsed = new AtomicBoolean();
        PluginDocumentStore delegate = new PluginDocumentStore() {
            @Override public Map<String, Object> save(String collection, String id, Map<String, Object> document) { return document; }
            @Override public Optional<Map<String, Object>> findById(String collection, String id) { return Optional.empty(); }
            @Override public List<Map<String, Object>> findAll(String collection, int page, int size) { return List.of(); }
            @Override public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
                fieldQueryUsed.set(true);
                return List.of(Map.of("id", "9", "ownerId", value));
            }
            @Override public long count(String collection) { countUsed.set(true); return 500; }
            @Override public void delete(String collection, String id) { }
        };
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);

        List<Map<String, Object>> matches = store.findByField("players", "ownerId", "42", 1, 20);
        assertTrue(fieldQueryUsed.get(), "无沙盒时必须走真实字段查询而不是整表分页过滤");
        assertEquals(1, matches.size());
        assertEquals(500, store.count("players"), "无沙盒时必须走真实 count，不能被 200 截断");
        assertTrue(countUsed.get());
    }

    @Test
    void findByFieldScansBeyondFirstPageWhenSandboxActive() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        // 目标文档排在第 30 条之后，旧实现只扫第一页必然漏掉
        for (int i = 0; i < 30; i++) {
            delegate.save("players", "other-" + i, Map.of("ownerId", "someone-else"));
        }
        delegate.save("players", "mine", Map.of("ownerId", "42"));
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            List<Map<String, Object>> matches = store.findByField("players", "ownerId", "42", 1, 20);
            assertEquals(1, matches.size());
            assertEquals("mine", matches.getFirst().get("id"));
        }
    }

    @Test
    void sandboxFindByFieldMergesOverlayChanges() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        delegate.save("players", "base-1", Map.of("ownerId", "42"));
        delegate.save("players", "base-2", Map.of("ownerId", "42"));
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            store.save("players", "sandbox-new", Map.of("ownerId", "42"));
            store.delete("players", "base-1");

            List<Map<String, Object>> matches = store.findByField("players", "ownerId", "42", 1, 20);
            assertEquals(2, matches.size());
            assertTrue(matches.stream().noneMatch(item -> "base-1".equals(item.get("id"))));
            assertTrue(matches.stream().anyMatch(item -> "sandbox-new".equals(item.get("id"))));

            // 匹配置内存分页：两页各一条且不重复
            List<Map<String, Object>> page1 = store.findByField("players", "ownerId", "42", 1, 1);
            List<Map<String, Object>> page2 = store.findByField("players", "ownerId", "42", 2, 1);
            assertEquals(1, page1.size());
            assertEquals(1, page2.size());
            assertFalse(page1.getFirst().get("id").equals(page2.getFirst().get("id")));
            assertTrue(store.findByField("players", "ownerId", "42", 3, 1).isEmpty());

            assertEquals(2, store.count("players"));
        }
    }

    @Test
    void sandboxFindAllPaginatesOverlayWithoutDuplicates() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            store.save("items", "a", Map.of("value", 1));
            store.save("items", "b", Map.of("value", 2));

            List<Map<String, Object>> page1 = store.findAll("items", 1, 1);
            List<Map<String, Object>> page2 = store.findAll("items", 2, 1);
            List<Map<String, Object>> page3 = store.findAll("items", 3, 1);
            assertEquals(1, page1.size());
            assertEquals(1, page2.size());
            assertFalse(page1.getFirst().get("id").equals(page2.getFirst().get("id")),
                    "overlay 条目不得在每一页重复出现");
            assertTrue(page3.isEmpty());
            assertEquals(2, store.count("items"));
        }
    }

    @Test
    void sandboxSaveStripsNullFieldsInsteadOfThrowing() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        SandboxAwarePluginDocumentStore store = new SandboxAwarePluginDocumentStore("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            Map<String, Object> document = new HashMap<>();
            document.put("ownerId", "42");
            document.put("skinHash", null);
            document.put("capeHash", null);
            Map<String, Object> saved = store.save("players", "p1", document);

            assertFalse(saved.containsKey("skinHash"));
            Map<String, Object> loaded = store.findById("players", "p1").orElseThrow();
            assertEquals("42", loaded.get("ownerId"));
            assertFalse(loaded.containsKey("capeHash"));
        }
    }
}
