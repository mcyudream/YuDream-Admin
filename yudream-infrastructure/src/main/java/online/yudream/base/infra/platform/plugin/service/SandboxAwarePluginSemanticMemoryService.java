package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryHit;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryQuery;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryRecord;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class SandboxAwarePluginSemanticMemoryService implements PluginSemanticMemoryService {
    private final String pluginCode;
    private final PluginSemanticMemoryService delegate;

    SandboxAwarePluginSemanticMemoryService(String pluginCode, PluginSemanticMemoryService delegate) {
        this.pluginCode = pluginCode;
        this.delegate = delegate;
    }

    @Override public PluginSemanticMemoryStatus status() { return delegate.status(); }

    @Override public CompletionStage<Void> index(PluginSemanticMemoryRecord record) {
        Map<String, Object> writes = QqSandboxExecutionScope.semanticWrites();
        if (writes == null) return delegate.index(record);
        writes.put(key(record.namespace(), record.id()), record);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespace", record.namespace());
        payload.put("id", record.id());
        payload.put("content", record.content());
        payload.put("metadata", record.metadata());
        record("semantic.index", payload);
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query) {
        Map<String, Object> writes = QqSandboxExecutionScope.semanticWrites();
        if (writes == null) return delegate.search(query);
        // 沙盒内索引的记录只存在于 overlay，必须并入搜索结果；读取仍落到真实存储，保持对生产的读一致性
        List<PluginSemanticMemoryHit> overlayHits = writes.values().stream()
                .filter(PluginSemanticMemoryRecord.class::isInstance)
                .map(PluginSemanticMemoryRecord.class::cast)
                .filter(item -> query == null || query.namespace() == null || query.namespace().equals(item.namespace()))
                .map(item -> new PluginSemanticMemoryHit(item.id(), item.content(), 1.0d, item.metadata()))
                .toList();
        if (overlayHits.isEmpty()) return delegate.search(query);
        return delegate.search(query).thenApply(base -> {
            Map<String, PluginSemanticMemoryHit> byId = new LinkedHashMap<>();
            overlayHits.forEach(hit -> byId.put(hit.id(), hit));
            base.forEach(hit -> byId.putIfAbsent(hit.id(), hit));
            int limit = query == null ? 10 : query.limit();
            return byId.values().stream().limit(Math.max(limit, 1)).toList();
        });
    }

    @Override public CompletionStage<Void> delete(String namespace, String id) {
        Map<String, Object> writes = QqSandboxExecutionScope.semanticWrites();
        if (writes == null) return delegate.delete(namespace, id);
        writes.remove(key(namespace, id));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespace", namespace);
        payload.put("id", id);
        record("semantic.delete", payload);
        return CompletableFuture.completedFuture(null);
    }

    private String key(String namespace, String id) {
        return namespace + ":" + id;
    }

    private void record(String action, Map<String, Object> payload) {
        var session = QqSandboxExecutionScope.requireActive();
        if (session != null) session.append("overlay", action, pluginCode, payload);
    }
}
