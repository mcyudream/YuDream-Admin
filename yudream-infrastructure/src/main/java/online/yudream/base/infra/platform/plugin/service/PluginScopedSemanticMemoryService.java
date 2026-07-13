package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryHit;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryQuery;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryRecord;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryStatus;

import java.util.List;
import java.util.concurrent.CompletionStage;

final class PluginScopedSemanticMemoryService implements PluginSemanticMemoryService {
    private final String pluginCode;
    private final PluginSemanticMemoryService delegate;

    PluginScopedSemanticMemoryService(String pluginCode, PluginSemanticMemoryService delegate) {
        this.pluginCode = pluginCode;
        this.delegate = delegate;
    }

    @Override public PluginSemanticMemoryStatus status() { return delegate.status(); }
    @Override public CompletionStage<Void> index(PluginSemanticMemoryRecord record) {
        return delegate.index(new PluginSemanticMemoryRecord(namespace(record.namespace()), record.id(), record.content(), record.providerCode(), record.modelCode(), record.metadata()));
    }
    @Override public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query) {
        return delegate.search(new PluginSemanticMemoryQuery(namespace(query.namespace()), query.text(), query.providerCode(), query.modelCode(), query.limit()));
    }
    @Override public CompletionStage<Void> delete(String namespace, String id) { return delegate.delete(namespace(namespace), id); }
    private String namespace(String local) { return pluginCode + ":" + (local == null ? "" : local.trim()); }
}
