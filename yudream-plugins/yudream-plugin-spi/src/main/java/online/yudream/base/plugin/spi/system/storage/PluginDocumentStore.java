package online.yudream.base.plugin.spi.system.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PluginDocumentStore {

    Map<String, Object> save(String collection, String id, Map<String, Object> document);

    Optional<Map<String, Object>> findById(String collection, String id);

    List<Map<String, Object>> findAll(String collection, int page, int size);

    List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size);

    long count(String collection);

    default boolean updateIfFieldAtMost(String collection, String id, String field, long maximum,
                                        Map<String, Object> document) {
        return false;
    }

    void delete(String collection, String id);
}
