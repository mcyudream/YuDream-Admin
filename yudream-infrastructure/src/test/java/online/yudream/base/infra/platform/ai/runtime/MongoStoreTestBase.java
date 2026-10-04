package online.yudream.base.infra.platform.ai.runtime;

import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;



/**
 * 直驱 MongoTemplate 内存桩：只实现本仓存储用到的等值/$regex 前缀查询、按 _id 排序与
 * findAndModify CAS，用于在无 MongoDB 的环境下验证存储键语义、版本 CAS 与检索行为。
 */
abstract class MongoStoreTestBase {

    protected StubMongoTemplate mongo;

    @BeforeEach
    void setUp() {
        mongo = new StubMongoTemplate();
    }

    static class StubMongoTemplate extends MongoTemplate {
        final Map<String, Document> rows = new ConcurrentHashMap<>();

        StubMongoTemplate() {
            super(MongoClients.create("mongodb://localhost:27017"), "stub");
        }

        @Override
        public <T> T save(T objectToSave) {
            Document document = new Document();
            getConverter().write(objectToSave, document);
            rows.put(String.valueOf(document.get("_id")), document);
            return objectToSave;
        }

        @Override
        public <T> T findById(Object id, Class<T> entityClass) {
            Document document = rows.get(String.valueOf(id));
            return document == null ? null : getConverter().read(entityClass, document);
        }

        @Override
        public <T> T findOne(Query query, Class<T> entityClass) {
            List<T> all = find(query, entityClass);
            return all.isEmpty() ? null : all.getFirst();
        }

        @Override
        public <T> List<T> find(Query query, Class<T> entityClass) {
            List<Document> matched = new ArrayList<>(rows.values().stream()
                    .filter(document -> matches(query.getQueryObject(), document))
                    .sorted(bySort(query.getSortObject()))
                    .toList());
            if (query.getSkip() > 0) {
                matched = matched.subList((int) Math.min(query.getSkip(), matched.size()), matched.size());
            }
            if (query.getLimit() > 0 && matched.size() > query.getLimit()) {
                matched = matched.subList(0, query.getLimit());
            }
            return matched.stream().map(document -> getConverter().read(entityClass, document)).toList();
        }

        @Override
        public long count(Query query, Class<?> entityClass) {
            return rows.values().stream().filter(document -> matches(query.getQueryObject(), document)).count();
        }

        @Override
        public <T> T findAndModify(Query query, org.springframework.data.mongodb.core.query.UpdateDefinition update,
                                   FindAndModifyOptions options, Class<T> entityClass) {
            for (Document document : rows.values()) {
                if (!matches(query.getQueryObject(), document)) {
                    continue;
                }
                Document set = ((Update) update).getUpdateObject().get("$set", Document.class);
                if (set != null) {
                    set.forEach(document::put);
                }
                return getConverter().read(entityClass, document);
            }
            return null;
        }

        @Override
        public com.mongodb.client.result.DeleteResult remove(Query query, Class<?> entityClass) {
            java.util.List<String> ids = rows.values().stream()
                    .filter(document -> matches(query.getQueryObject(), document))
                    .map(document -> String.valueOf(document.get("_id")))
                    .toList();
            ids.forEach(rows::remove);
            return com.mongodb.client.result.DeleteResult.acknowledged(ids.size());
        }

        @Override
        public <T> java.util.List<T> findDistinct(String field, Class<?> entityClass, Class<T> valueClass) {
            return rows.values().stream()
                    .map(document -> valueClass.cast(document.get(field)))
                    .distinct()
                    .toList();
        }

        private boolean matches(Document criteria, Document document) {
            for (Map.Entry<String, Object> entry : criteria.entrySet()) {
                if (!matchesValue(document.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private boolean matchesValue(Object actual, Object expected) {
            if (expected instanceof Document operator) {
                Object regex = operator.get("$regex");
                if (regex != null) {
                    return actual != null && Pattern.compile(String.valueOf(regex)).matcher(String.valueOf(actual)).find();
                }
                return false;
            }
            return actual != null && actual.equals(expected);
        }

        private Comparator<Document> bySort(Document sortObject) {
            if (sortObject == null || sortObject.isEmpty()) {
                return Comparator.comparing(document -> String.valueOf(document.get("_id")));
            }
            return (left, right) -> {
                for (Map.Entry<String, Object> order : sortObject.entrySet()) {
                    int compared = String.valueOf(left.get(order.getKey()))
                            .compareTo(String.valueOf(right.get(order.getKey())));
                    if (compared != 0) {
                        double direction = ((Number) order.getValue()).doubleValue();
                        return direction < 0 ? -compared : compared;
                    }
                }
                return 0;
            };
        }
    }
}
