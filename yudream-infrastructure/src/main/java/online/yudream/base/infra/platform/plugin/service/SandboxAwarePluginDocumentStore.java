package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SandboxAwarePluginDocumentStore implements PluginDocumentStore {
    private static final String DELETED = "__sandboxDeleted";
    private static final int SCAN_PAGE_SIZE = 200;
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
        Map<String, Object> value = new LinkedHashMap<>();
        if (document != null) {
            // Map.copyOf 遇 null 直接 NPE，沙盒副本统一剔除 null 字段；读取侧 get() 语义与 null 等价
            document.forEach((key, item) -> {
                if (key != null && item != null) value.put(key, item);
            });
        }
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
        var overlay = overlay();
        if (overlay == null) return delegate.findAll(collection, page, size);
        return pageOf(merged(collection, overlay), page, size);
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
        var overlay = overlay();
        if (overlay == null) return delegate.findByField(collection, field, value, page, size);
        List<Map<String, Object>> matches = merged(collection, overlay).stream()
                .filter(item -> java.util.Objects.equals(item.get(field), value))
                .toList();
        return pageOf(matches, page, size);
    }

    @Override
    public long count(String collection) {
        var overlay = overlay();
        if (overlay == null) return delegate.count(collection);
        return merged(collection, overlay).size();
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

    /**
     * 合并基库与沙盒 overlay 的全量视图：基库按分页扫描到底，overlay 覆盖同 id 条目、
     * 删除墓碑剔除对应条目。沙盒数据量小，全量合并后在内存里统一分页，
     * 避免「基库先分页再过滤/追加」导致的漏数据、跨页重复与扫描死循环。
     */
    private List<Map<String, Object>> merged(String collection, Map<String, Map<String, Map<String, Object>>> overlay) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        int page = 1;
        while (true) {
            List<Map<String, Object>> batch = delegate.findAll(collection, page, SCAN_PAGE_SIZE);
            batch.forEach(item -> byId.put(String.valueOf(item.get("id")), item));
            if (batch.size() < SCAN_PAGE_SIZE) break;
            page++;
        }
        overlay.getOrDefault(collection, Map.of()).forEach((id, value) -> {
            if (Boolean.TRUE.equals(value.get(DELETED))) byId.remove(id);
            else byId.put(id, value);
        });
        return List.copyOf(byId.values());
    }

    private List<Map<String, Object>> pageOf(List<Map<String, Object>> items, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        long from = Math.min((long) (safePage - 1) * safeSize, items.size());
        long to = Math.min(from + safeSize, items.size());
        return items.subList((int) from, (int) to);
    }

    private Map<String, Map<String, Map<String, Object>>> overlay() {
        return QqSandboxExecutionScope.documents();
    }

    private void record(String action, Map<String, Object> payload) {
        var session = QqSandboxExecutionScope.requireActive();
        if (session != null) session.append("overlay", action, pluginCode, payload);
    }
}
