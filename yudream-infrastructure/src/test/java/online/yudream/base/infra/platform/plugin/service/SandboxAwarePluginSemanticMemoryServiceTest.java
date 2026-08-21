package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryHit;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryQuery;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryRecord;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxAwarePluginSemanticMemoryServiceTest {

    /** 模拟真实向量库：只持有基础命中，不知道沙盒 overlay */
    private static final class InMemoryDelegate implements PluginSemanticMemoryService {
        private final List<PluginSemanticMemoryHit> baseHits = new ArrayList<>();
        private final List<PluginSemanticMemoryRecord> indexed = new ArrayList<>();

        @Override public PluginSemanticMemoryStatus status() { return PluginSemanticMemoryStatus.unavailable("test"); }

        @Override public CompletionStage<Void> index(PluginSemanticMemoryRecord record) {
            indexed.add(record);
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query) {
            return CompletableFuture.completedFuture(List.copyOf(baseHits));
        }

        @Override public CompletionStage<Void> delete(String namespace, String id) {
            baseHits.removeIf(hit -> hit.id().equals(id));
            return CompletableFuture.completedFuture(null);
        }
    }

    private QqSandboxSession session() {
        return QqSandboxSession.create("sandbox", "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 60_000L, Instant.now());
    }

    private PluginSemanticMemoryQuery query(String namespace) {
        return new PluginSemanticMemoryQuery(namespace, "hello", null, null, 10);
    }

    @Test
    void delegatesDirectlyWhenNoSandbox() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        delegate.baseHits.add(new PluginSemanticMemoryHit("base-1", "content", 0.9d, Map.of()));
        AtomicBoolean searchUsed = new AtomicBoolean();
        PluginSemanticMemoryService spying = new PluginSemanticMemoryService() {
            @Override public PluginSemanticMemoryStatus status() { return delegate.status(); }
            @Override public CompletionStage<Void> index(PluginSemanticMemoryRecord record) { return delegate.index(record); }
            @Override public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery q) {
                searchUsed.set(true);
                return delegate.search(q);
            }
            @Override public CompletionStage<Void> delete(String namespace, String id) { return delegate.delete(namespace, id); }
        };
        SandboxAwarePluginSemanticMemoryService service = new SandboxAwarePluginSemanticMemoryService("demo", spying);

        List<PluginSemanticMemoryHit> hits = service.search(query("ns")).toCompletableFuture().join();
        assertTrue(searchUsed.get(), "无沙盒时必须直接走真实检索");
        assertEquals(1, hits.size());
        assertEquals("base-1", hits.getFirst().id());
    }

    @Test
    void sandboxIndexedRecordsAreVisibleToSearch() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        delegate.baseHits.add(new PluginSemanticMemoryHit("base-1", "persisted", 0.9d, Map.of()));
        SandboxAwarePluginSemanticMemoryService service = new SandboxAwarePluginSemanticMemoryService("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            service.index(new PluginSemanticMemoryRecord("ns", "sandbox-1", "overlay content", null, null, Map.of()))
                    .toCompletableFuture().join();

            List<PluginSemanticMemoryHit> hits = service.search(query("ns")).toCompletableFuture().join();
            assertEquals(2, hits.size(), "沙盒内索引的记录必须并入搜索结果");
            assertEquals("sandbox-1", hits.getFirst().id(), "overlay 命中应优先于基础命中");
            assertTrue(hits.stream().anyMatch(hit -> "base-1".equals(hit.id())));

            // 其他命名空间不应看到该记录
            assertTrue(service.search(query("other")).toCompletableFuture().join().stream()
                    .noneMatch(hit -> "sandbox-1".equals(hit.id())));
        }

        assertTrue(delegate.indexed.isEmpty(), "沙盒索引不得写入真实存储");
    }

    @Test
    void sandboxHitDeduplicatesBaseHitWithSameId() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        delegate.baseHits.add(new PluginSemanticMemoryHit("dup", "old content", 0.5d, Map.of()));
        SandboxAwarePluginSemanticMemoryService service = new SandboxAwarePluginSemanticMemoryService("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            service.index(new PluginSemanticMemoryRecord("ns", "dup", "new content", null, null, Map.of()))
                    .toCompletableFuture().join();

            List<PluginSemanticMemoryHit> hits = service.search(query("ns")).toCompletableFuture().join();
            assertEquals(1, hits.size(), "相同 id 的 overlay 与基础命中必须去重");
            assertEquals("new content", hits.getFirst().content());
        }
    }

    @Test
    void sandboxIndexWithNullContentDoesNotThrow() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        SandboxAwarePluginSemanticMemoryService service = new SandboxAwarePluginSemanticMemoryService("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            assertDoesNotThrow(() -> service.index(new PluginSemanticMemoryRecord("ns", "n1", null, null, null, null))
                    .toCompletableFuture().join(), "content/metadata 为 null 时不得因 Map.of/copyOf 抛 NPE");
            List<PluginSemanticMemoryHit> hits = service.search(query("ns")).toCompletableFuture().join();
            assertEquals(1, hits.size());
        }
    }

    @Test
    void sandboxDeleteRemovesOverlayEntry() {
        InMemoryDelegate delegate = new InMemoryDelegate();
        SandboxAwarePluginSemanticMemoryService service = new SandboxAwarePluginSemanticMemoryService("demo", delegate);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session())) {
            service.index(new PluginSemanticMemoryRecord("ns", "sandbox-1", "content", null, null, Map.of()))
                    .toCompletableFuture().join();
            service.delete("ns", "sandbox-1").toCompletableFuture().join();

            assertTrue(service.search(query("ns")).toCompletableFuture().join().isEmpty(),
                    "沙盒删除后 overlay 记录不得再出现在搜索结果中");
        }
    }
}
