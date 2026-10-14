package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.platform.ai.dataobj.AgentWorkspaceFileDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
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
    public boolean supportsAtomicSessionStorage() {
        return true;
    }

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
        Update update = new Update().set("value", data).inc("dataVersion", 1L)
                .set("updateTime", now()).setOnInsert("createTime", now())
                .setOnInsert("namespace", namespacePath(namespace)).setOnInsert("key", key);
        // 单文档原子递增；不能先读版本再 save，否则其他副本的日志 CAS 会被覆盖。
        mongo.findAndModify(Query.query(Criteria.where("_id").is(id)), update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), AgentWorkspaceFileDO.class);
    }

    @Override
    public boolean putIfVersion(List<String> namespace, String key, Map<String, Object> data, long version) {
        if (version < 0) {
            return false;
        }
        String id = storeKey(namespace, key);
        Criteria criteria = Criteria.where("_id").is(id);
        if (version == 0) {
            criteria.andOperator(new Criteria().orOperator(
                    Criteria.where("dataVersion").is(0), Criteria.where("dataVersion").exists(false)));
        } else {
            criteria.and("dataVersion").is(version);
        }
        Query query = Query.query(criteria);
        Update update = new Update()
                .set("value", data)
                .set("dataVersion", version + 1)
                .set("updateTime", now());
        AgentWorkspaceFileDO updated = mongo.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(false), AgentWorkspaceFileDO.class);
        if (updated != null) {
            return true;
        }
        if (version != 0) {
            return false;
        }
        AgentWorkspaceFileDO initial = new AgentWorkspaceFileDO();
        initial.setStoreKey(id);
        initial.setNamespace(namespacePath(namespace));
        initial.setKey(key);
        initial.setValue(data);
        initial.setDataVersion(1L);
        initial.setCreateTime(now());
        initial.setUpdateTime(now());
        try {
            mongo.insert(initial);
            return true;
        } catch (DuplicateKeyException conflict) {
            return false;
        }
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int limit, int offset) {
        if (limit <= 0) {
            return List.of();
        }
        String prefix = namespacePath(namespace);
        // 显式 $regex 前缀查询（BasicQuery 直存查询文档），语义与框架 InMemoryStore 的
        // compoundKey startsWith 前缀匹配一致：命中命名空间内全部文件（含子路径键）。
        org.bson.Document queryObject = new org.bson.Document("_id",
                new org.bson.Document("$regex", "^" + java.util.regex.Pattern.quote(
                        prefix.isEmpty() ? "" : prefix + "/")));
        org.springframework.data.mongodb.core.query.BasicQuery query =
                new org.springframework.data.mongodb.core.query.BasicQuery(queryObject);
        query.with(Sort.by(Sort.Direction.ASC, "_id"));
        query.skip(Math.max(0, offset));
        query.limit(Math.max(0, limit));
        return mongo.find(query, AgentWorkspaceFileDO.class).stream()
                .map(doc -> new StoreItem(prefix.isEmpty() ? doc.getStoreKey()
                        : doc.getStoreKey().substring(prefix.length() + 1), doc.getValue(), doc.getDataVersion()))
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
