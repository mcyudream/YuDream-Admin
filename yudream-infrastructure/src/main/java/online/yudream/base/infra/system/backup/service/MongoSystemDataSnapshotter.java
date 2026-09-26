package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.service.SystemDataSnapshotter;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Mongo 全量快照：枚举主库业务集合并流式导出 EJSON EXTENDED 文档（类型无损）。
 * 排除策略见 {@link SnapshotExclusions}：系统内部集合与备份自身集合硬排除，
 * 日志/遥测/短时效令牌类默认排除且可经 {@code yudream.system.backup.exclude-collections}
 * 整组覆盖（支持「前缀*」通配）。
 */
@Service
public class MongoSystemDataSnapshotter implements SystemDataSnapshotter {

    private final MongoTemplate mongo;
    private final SnapshotExclusions exclusions;

    public MongoSystemDataSnapshotter(
            MongoTemplate mongo,
            @Value("${yudream.system.backup.exclude-collections:}") String excludeCollections) {
        this.mongo = mongo;
        this.exclusions = new SnapshotExclusions(excludeCollections);
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
            if (views.contains(name) || exclusions.excluded(name)) {
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
    public List<String> excludedCollections() {
        return exclusions.describe();
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
