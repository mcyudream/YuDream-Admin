package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.service.BackupRestoreStore;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.infra.system.integration.StorageClientProvider;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 合并导入目标实现：文档经 EJSON 解析后按批 bulkWrite（插入或 upsert 覆盖）；
 * 对象冲突判定基于全桶现有键集合（懒加载一次），写入复用系统对象存储端口。
 */
@Service
public class MongoBackupRestoreStore implements BackupRestoreStore {

    private static final int BATCH_SIZE = 500;
    private static final int KEY_PROBE_BATCH = 200;

    private final MongoTemplate mongo;
    private final StorageClientProvider storage;
    private final ObjectStorage objectStorage;

    private volatile Set<String> existingObjectKeysCache;

    public MongoBackupRestoreStore(MongoTemplate mongo, StorageClientProvider storage, ObjectStorage objectStorage) {
        this.mongo = mongo;
        this.storage = storage;
        this.objectStorage = objectStorage;
    }

    @Override
    public Set<String> existingDocumentIds(String collection, Set<String> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Set.of();
        }
        Set<String> existing = new HashSet<>();
        List<String> candidates = new ArrayList<>(candidateIds);
        for (int from = 0; from < candidates.size(); from += KEY_PROBE_BATCH) {
            List<Object> variants = new ArrayList<>();
            for (String id : candidates.subList(from, Math.min(from + KEY_PROBE_BATCH, candidates.size()))) {
                variants.addAll(idVariants(id));
            }
            try (var cursor = mongo.getCollection(collection)
                    .find(new Document("_id", new Document("$in", variants)))
                    .projection(new Document("_id", 1))
                    .iterator()) {
                while (cursor.hasNext()) {
                    existing.add(MongoSystemDataSnapshotter.normalizeId(cursor.next().get("_id")));
                }
            } catch (Exception e) {
                throw new BizException("比对集合数据失败：" + collection);
            }
        }
        return existing;
    }

    @Override
    public void writeDocuments(String collection, Collection<SnapshotDocument> documents, boolean replaceExisting) {
        if (documents.isEmpty()) {
            return;
        }
        List<com.mongodb.bulk.BulkWriteResult> results = new ArrayList<>();
        List<Document> parsed = new ArrayList<>(documents.size());
        for (SnapshotDocument snapshot : documents) {
            try {
                parsed.add(Document.parse(snapshot.ejson()));
            } catch (Exception e) {
                throw new BizException("解析备份数据失败（集合 " + collection + "）：" + e.getMessage());
            }
        }
        for (int from = 0; from < parsed.size(); from += BATCH_SIZE) {
            List<com.mongodb.client.model.WriteModel<Document>> models = new ArrayList<>();
            for (Document document : parsed.subList(from, Math.min(from + BATCH_SIZE, parsed.size()))) {
                if (replaceExisting) {
                    models.add(new com.mongodb.client.model.ReplaceOneModel<>(
                            new Document("_id", document.get("_id")), document,
                            new com.mongodb.client.model.ReplaceOptions().upsert(true)));
                } else {
                    models.add(new com.mongodb.client.model.InsertOneModel<>(document));
                }
            }
            try {
                results.add(mongo.getCollection(collection).bulkWrite(models,
                        new com.mongodb.client.model.BulkWriteOptions().ordered(false)));
            } catch (org.bson.BsonInvalidOperationException e) {
                throw new BizException("写入备份数据失败（集合 " + collection + "）：" + e.getMessage());
            } catch (Exception e) {
                throw new BizException("写入备份数据失败（集合 " + collection + "）：" + rootMessage(e));
            }
        }
    }

    @Override
    public Set<String> existingObjectKeys(Collection<String> keys) {
        Set<String> existing = existingObjectKeys();
        Set<String> result = new HashSet<>();
        for (String key : keys) {
            if (existing.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    @Override
    public void writeObject(String objectKey, InputStream in, long size) {
        try {
            objectStorage.put(objectKey, in, size, "application/octet-stream");
        } catch (S3Exception e) {
            throw new BizException("恢复对象失败：" + objectKey);
        }
    }

    private Set<String> existingObjectKeys() {
        Set<String> cache = existingObjectKeysCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (existingObjectKeysCache == null) {
                Set<String> keys = new HashSet<>();
                String bucket = storage.config().bucket();
                var paginator = storage.client().listObjectsV2Paginator(
                        software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                                .bucket(bucket).build());
                paginator.contents().forEach(summary -> keys.add(summary.key()));
                existingObjectKeysCache = keys;
            }
            return existingObjectKeysCache;
        }
    }

    /** 同一规范化 id 的本地存在性需按可能的原生类型探测（雪花 Long / 字符串 / ObjectId）。 */
    static List<Object> idVariants(String id) {
        List<Object> variants = new ArrayList<>(3);
        if (id == null || id.isEmpty()) {
            return variants;
        }
        // 负数雪花 ID（种子数据常见，如 -1740829301）：去掉符号位后再判数值形态
        String unsigned = id.startsWith("-") ? id.substring(1) : id;
        if (unsigned.length() == 24 && unsigned.chars().allMatch(Character::isLetterOrDigit)) {
            try {
                variants.add(new ObjectId(unsigned));
            } catch (IllegalArgumentException ignored) {
                // 非 ObjectId 十六进制串按字符串处理
            }
        }
        if (!unsigned.isEmpty() && unsigned.length() <= 19 && unsigned.chars().allMatch(Character::isDigit)) {
            try {
                variants.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                // 超长数字串按字符串处理
            }
        }
        variants.add(id);
        return variants;
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
