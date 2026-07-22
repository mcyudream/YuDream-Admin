package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class MongoPluginDocumentStore implements PluginDocumentStore {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final int MAX_PAGE_SIZE = 200;

    private final String pluginCode;
    private final String safePluginCode;
    private final MongoTemplate mongoTemplate;

    public MongoPluginDocumentStore(String pluginCode, MongoTemplate mongoTemplate) {
        this.pluginCode = requireSegment(pluginCode, "插件编码不合法");
        this.safePluginCode = pluginCode.replace('-', '_');
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
        String safeId = requireText(id, "文档 ID 不能为空");
        validateDocument(document);
        Document mongoDocument = new Document(document == null ? Map.of() : document);
        mongoDocument.put("_id", safeId);
        mongoDocument.put("id", safeId);
        mongoDocument.put("pluginCode", pluginCode);
        Document saved = mongoTemplate.save(mongoDocument, collectionName(collection));
        return toMap(saved);
    }

    @Override
    public Optional<Map<String, Object>> findById(String collection, String id) {
        Query query = scopedQuery().addCriteria(Criteria.where("_id").is(requireText(id, "文档 ID 不能为空")));
        Document document = mongoTemplate.findOne(query, Document.class, collectionName(collection));
        return Optional.ofNullable(document).map(this::toMap);
    }

    @Override
    public List<Map<String, Object>> findAll(String collection, int page, int size) {
        Query query = scopedQuery()
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .skip(skip(page, size))
                .limit(limit(size));
        return mongoTemplate.find(query, Document.class, collectionName(collection)).stream()
                .map(this::toMap)
                .toList();
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
        String safeField = requireField(field);
        Query query = scopedQuery()
                .addCriteria(Criteria.where(safeField).is(value))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .skip(skip(page, size))
                .limit(limit(size));
        return mongoTemplate.find(query, Document.class, collectionName(collection)).stream()
                .map(this::toMap)
                .toList();
    }

    @Override
    public long count(String collection) {
        return mongoTemplate.count(scopedQuery(), collectionName(collection));
    }

    @Override
    public boolean updateIfFieldAtMost(String collection, String id, String field, long maximum,
                                       Map<String, Object> document) {
        String safeId = requireText(id, "Document ID must not be blank");
        String safeField = requireField(field);
        validateDocument(document);
        Query query = scopedQuery()
                .addCriteria(Criteria.where("_id").is(safeId))
                .addCriteria(Criteria.where(safeField).lte(maximum));
        Update update = new Update();
        document.forEach((key, value) -> {
            if (!"id".equals(key)) update.set(key, value);
        });
        update.set("pluginCode", pluginCode).set("id", safeId);
        return mongoTemplate.findAndModify(query, update, Document.class, collectionName(collection)) != null;
    }

    @Override
    public void delete(String collection, String id) {
        Query query = scopedQuery().addCriteria(Criteria.where("_id").is(requireText(id, "文档 ID 不能为空")));
        mongoTemplate.remove(query, collectionName(collection));
    }

    private Query scopedQuery() {
        return new Query(Criteria.where("pluginCode").is(pluginCode));
    }

    private String collectionName(String collection) {
        String safeCollection = requireSegment(collection, "插件集合名称不合法").replace('-', '_');
        return "plugin_" + safePluginCode + "__" + safeCollection;
    }

    private Map<String, Object> toMap(Document document) {
        Map<String, Object> result = new LinkedHashMap<>();
        String id = document.getString("_id");
        document.forEach((key, value) -> {
            if (!"_id".equals(key) && !"pluginCode".equals(key)) {
                result.put(key, toPlainValue(value));
            }
        });
        result.putIfAbsent("id", id);
        return result;
    }

    private Object toPlainValue(Object value) {
        if (value instanceof Document document) {
            Map<String, Object> result = new LinkedHashMap<>();
            document.forEach((key, item) -> result.put(key, toPlainValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::toPlainValue).toList();
        }
        return value;
    }

    private void validateDocument(Map<String, Object> document) {
        if (document == null) {
            return;
        }
        document.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || key.startsWith("$") || key.contains(".")) {
                throw new BizException("插件文档字段不合法：" + key);
            }
            if ("_id".equals(key) || "pluginCode".equals(key)) {
                throw new BizException("插件文档字段为系统保留字段：" + key);
            }
            validateValue(value);
        });
    }

    @SuppressWarnings("unchecked")
    private void validateValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            validateDocument((Map<String, Object>) map);
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(this::validateValue);
        }
    }

    private long skip(int page, int size) {
        int safePage = Math.max(page, 1);
        return (long) (safePage - 1) * limit(size);
    }

    private int limit(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String requireField(String field) {
        String safeField = requireText(field, "查询字段不能为空");
        if (!FIELD_PATTERN.matcher(safeField).matches() || safeField.startsWith("$") || safeField.contains("..")) {
            throw new BizException("查询字段不合法：" + field);
        }
        return safeField;
    }

    private String requireSegment(String value, String message) {
        String text = requireText(value, message);
        if (!SEGMENT_PATTERN.matcher(text).matches()) {
            throw new BizException(message + "：" + value);
        }
        return text;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
