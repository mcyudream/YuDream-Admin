package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryHit;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryQuery;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryRecord;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryStatus;

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
        String key = record.namespace() + ":" + record.id();
        writes.put(key, record);
        record("semantic.index", Map.of("namespace", record.namespace(), "id", record.id(),
                "content", record.content(), "metadata", record.metadata()));
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query) {
        return delegate.search(query);
    }

    @Override public CompletionStage<Void> delete(String namespace, String id) {
        Map<String, Object> writes = QqSandboxExecutionScope.semanticWrites();
        if (writes == null) return delegate.delete(namespace, id);
        writes.remove(namespace + ":" + id);
        record("semantic.delete", Map.of("namespace", namespace, "id", id));
        return CompletableFuture.completedFuture(null);
    }

    private void record(String action, Map<String, Object> payload) {
        var session = QqSandboxExecutionScope.requireActive();
        if (session != null) session.append("overlay", action, pluginCode, payload);
    }
}
