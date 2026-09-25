package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.service.SystemDataSnapshotter;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Mongo 全量快照：枚举主库业务集合并流式导出 EJSON EXTENDED 文档（类型无损）。
 * 排除系统内部集合与备份自身集合，避免快照包含备份元数据。
 */
@Service
public class MongoSystemDataSnapshotter implements SystemDataSnapshotter {

    private static final Set<String> EXCLUDED_COLLECTIONS = Set.of(
            "sysBackupJob", "sysBackupTarget", "sysBackupPlan");

    private final MongoTemplate mongo;

    public MongoSystemDataSnapshotter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<CollectionSummary> collections() {
        Set<String> views = new HashSet<>();
        mongo.getDb().listCollections().forEach(info -> {
            if ("view".equals(info.get("type"))) {
                views.add(info.getString("name"));
            }
        });
        List<CollectionSummary> summaries = new ArrayList<>();
        for (String name : mongo.getCollectionNames()) {
            if (name.startsWith("system.") || EXCLUDED_COLLECTIONS.contains(name) || views.contains(name)) {
                continue;
            }
            try {
                summaries.add(new CollectionSummary(name, mongo.getCollection(name).countDocuments()));
            } catch (Exception ignored) {
                // 单个集合统计失败不阻塞整体快照（例如正在重建索引），导出阶段再如实报错
            }
        }
        return summaries;
    }

    @Override
    public void streamCollection(String name, Consumer<SnapshotDocument> consumer) {
        JsonWriterSettings settings = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
        try (var cursor = mongo.getCollection(name).find().iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                consumer.accept(new SnapshotDocument(normalizeId(document.get("_id")), document.toJson(settings)));
            }
        }
    }

    /** 规范化 _id 为字符串：ObjectId 十六进制、数值/字符串原样，其余 toString。 */
    static String normalizeId(Object id) {
        if (id instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return String.valueOf(id);
    }
}
