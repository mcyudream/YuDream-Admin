package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SandboxAwarePluginDocumentStore implements PluginDocumentStore {
    private static final String DELETED = "__sandboxDeleted";
    private final String pluginCode;
    private final PluginDocumentStore delegate;

    SandboxAwarePluginDocumentStore(String pluginCode, PluginDocumentStore delegate) {
        this.pluginCode = pluginCode;
        this.delegate = delegate;
    }

    @Override
    public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
        var overlay = overlay();
        if (overlay == null) return delegate.save(collection, id, document);
        Map<String, Object> value = new LinkedHashMap<>(document == null ? Map.of() : document);
        value.put("id", id);
        overlay.computeIfAbsent(collection, ignored -> new java.util.concurrent.ConcurrentHashMap<>()).put(id, Map.copyOf(value));
        record("documents.save", Map.of("collection", collection, "id", id, "document", value));
        return Map.copyOf(value);
    }

    @Override
    public Optional<Map<String, Object>> findById(String collection, String id) {
        var overlay = overlay();
        if (overlay != null && overlay.getOrDefault(collection, Map.of()).containsKey(id)) {
            Map<String, Object> value = overlay.get(collection).get(id);
            return Boolean.TRUE.equals(value.get(DELETED)) ? Optional.empty() : Optional.of(value);
        }
        return delegate.findById(collection, id);
    }

    @Override
    public List<Map<String, Object>> findAll(String collection, int page, int size) {
        List<Map<String, Object>> base = new java.util.ArrayList<>(delegate.findAll(collection, page, size));
        var overlay = overlay();
        if (overlay != null) overlay.getOrDefault(collection, Map.of()).values().forEach(value -> {
            base.removeIf(item -> String.valueOf(item.get("id")).equals(String.valueOf(value.get("id"))));
            if (!Boolean.TRUE.equals(value.get(DELETED))) base.add(value);
        });
        return List.copyOf(base);
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
        return findAll(collection, page, size).stream().filter(item -> java.util.Objects.equals(item.get(field), value)).toList();
    }

    @Override
    public long count(String collection) {
        return findAll(collection, 1, 200).size();
    }

    @Override
    public boolean updateIfFieldAtMost(String collection, String id, String field, long maximum, Map<String, Object> document) {
        if (overlay() == null) return delegate.updateIfFieldAtMost(collection, id, field, maximum, document);
        Optional<Map<String, Object>> current = findById(collection, id);
        if (current.isEmpty() || !(current.get().get(field) instanceof Number number) || number.longValue() > maximum)
            return false;
        save(collection, id, document);
        return true;
    }

    @Override
    public void delete(String collection, String id) {
        if (overlay() == null) {
            delegate.delete(collection, id);
            return;
        }
        overlay().computeIfAbsent(collection, ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(id, Map.of("id", id, DELETED, true));
        record("documents.delete", Map.of("collection", collection, "id", id));
    }

    private Map<String, Map<String, Map<String, Object>>> overlay() {
        return QqSandboxExecutionScope.documents();
    }

    private void record(String action, Map<String, Object> payload) {
        var session = QqSandboxExecutionScope.requireActive();
        if (session != null) session.append("overlay", action, pluginCode, payload);
    }
}
