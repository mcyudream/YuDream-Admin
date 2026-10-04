package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.platform.ai.dataobj.AgentWorkspaceFileDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Harness 共享存储（RemoteFilesystemSpec 的 BaseStore）MongoDB 实现：
 * 工作区运行时产物（MEMORY.md、memory/、skills/、会话日志等）按命名空间路由落库，
 * 多副本部署下任意节点可读写同一份工作区。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class MongoBaseStore implements BaseStore {

    private final MongoTemplate mongo;

    @Override
    public StoreItem get(List<String> namespace, String key) {
        AgentWorkspaceFileDO doc = mongo.findById(storeKey(namespace, key), AgentWorkspaceFileDO.class);
        if (doc == null) {
            return null;
        }
        return new StoreItem(key, doc.getValue(), doc.getDataVersion());
    }

    @Override
    public void put(List<String> namespace, String key, Map<String, Object> data) {
        String id = storeKey(namespace, key);
        AgentWorkspaceFileDO doc = mongo.findById(id, AgentWorkspaceFileDO.class);
        if (doc == null) {
            doc = new AgentWorkspaceFileDO();
            doc.setStoreKey(id);
            doc.setNamespace(namespacePath(namespace));
            doc.setKey(key);
            doc.setDataVersion(1L);
            doc.setCreateTime(now());
        } else {
            doc.setDataVersion(doc.getDataVersion() + 1);
        }
        doc.setValue(data);
        doc.setUpdateTime(now());
        mongo.save(doc);
    }

    @Override
    public boolean putIfVersion(List<String> namespace, String key, Map<String, Object> data, long version) {
        Query query = Query.query(Criteria.where("_id").is(storeKey(namespace, key)).and("dataVersion").is(version));
        Update update = new Update()
                .set("value", data)
                .set("dataVersion", version + 1)
                .set("updateTime", now());
        AgentWorkspaceFileDO updated = mongo.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(false), AgentWorkspaceFileDO.class);
        return updated != null;
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int offset, int limit) {
        String prefix = namespacePath(namespace);
        // 显式 $regex 前缀查询（BasicQuery 直存查询文档），语义与框架 InMemoryStore 的
        // compoundKey startsWith 前缀匹配一致：命中命名空间内全部文件（含子路径键）。
        org.bson.Document queryObject = new org.bson.Document("_id",
                new org.bson.Document("$regex", "^" + java.util.regex.Pattern.quote(prefix) + "/"));
        org.springframework.data.mongodb.core.query.BasicQuery query =
                new org.springframework.data.mongodb.core.query.BasicQuery(queryObject);
        query.with(Sort.by(Sort.Direction.ASC, "_id"));
        query.skip(Math.max(0, offset));
        query.limit(Math.max(0, limit));
        return mongo.find(query, AgentWorkspaceFileDO.class).stream()
                .map(doc -> new StoreItem(doc.getKey(), doc.getValue(), doc.getDataVersion()))
                .toList();
    }

    @Override
    public void delete(List<String> namespace, String key) {
        mongo.remove(Query.query(Criteria.where("_id").is(storeKey(namespace, key))), AgentWorkspaceFileDO.class);
    }

    static String storeKey(List<String> namespace, String key) {
        String ns = namespacePath(namespace);
        return StringUtils.hasText(ns) ? ns + "/" + key : key;
    }

    private static String namespacePath(List<String> namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return "";
        }
        return String.join("/", namespace.stream().filter(StringUtils::hasText).map(String::trim).toList());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
