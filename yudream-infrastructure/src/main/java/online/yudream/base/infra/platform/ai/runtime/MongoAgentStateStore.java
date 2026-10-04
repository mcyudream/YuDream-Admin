package online.yudream.base.infra.platform.ai.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.platform.ai.dataobj.AgentStateDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AgentStateStore 的 MongoDB 实现：跨副本共享会话状态（对话上下文、压缩摘要、权限规则、
 * Plan Mode、任务清单），替代框架默认的单机 JsonFileAgentStateStore。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class MongoAgentStateStore implements AgentStateStore {

    static final String ANONYMOUS_USER = "__anon__";

    private final MongoTemplate mongo;

    @Override
    public void save(String agentId, String userId, String sessionId, State state) {
        String json = JsonUtils.getJsonCodec().toJson(state);
        mutate(document(agentId, userId, sessionId), doc -> doc.setStateJson(json));
    }

    @Override
    public void save(String agentId, String userId, String sessionId, List<? extends State> states) {
        String json = JsonUtils.getJsonCodec().toJson(states);
        mutate(document(agentId, userId, sessionId), doc -> doc.setListJson(json));
    }

    @Override
    public <T extends State> Optional<T> get(String agentId, String userId, String sessionId, Class<T> type) {
        AgentStateDO doc = load(agentId, userId, sessionId);
        if (doc == null || !StringUtils.hasText(doc.getStateJson())) {
            return Optional.empty();
        }
        return Optional.of(JsonUtils.getJsonCodec().fromJson(doc.getStateJson(), type));
    }

    @Override
    public <T extends State> List<T> getList(String agentId, String userId, String sessionId, Class<T> type) {
        AgentStateDO doc = load(agentId, userId, sessionId);
        if (doc == null || !StringUtils.hasText(doc.getListJson())) {
            return List.of();
        }
        List<java.util.Map<String, Object>> raw = JsonUtils.getJsonCodec()
                .fromJson(doc.getListJson(), new TypeReference<>() {
                });
        return raw.stream().map(item -> JsonUtils.getJsonCodec().convertValue(item, type)).toList();
    }

    @Override
    public boolean exists(String agentId, String userId) {
        return mongo.count(userQuery(agentId, userId), AgentStateDO.class) > 0;
    }

    @Override
    public void delete(String agentId, String userId) {
        mongo.remove(userQuery(agentId, userId), AgentStateDO.class);
    }

    @Override
    public void delete(String agentId, String userId, String sessionId) {
        mongo.remove(Query.query(Criteria.where("_id").is(documentId(agentId, userId, sessionId))), AgentStateDO.class);
    }

    @Override
    public Set<String> listSessionIds(String agentId) {
        return new LinkedHashSet<>(mongo.findDistinct("sessionId", AgentStateDO.class, String.class));
    }

    private Optional<String> normalizedUser(String agentId, String userId) {
        return Optional.of(StringUtils.hasText(userId) ? userId : ANONYMOUS_USER);
    }

    private Query userQuery(String agentId, String userId) {
        return Query.query(normalizedUser(agentId, userId)
                .map(value -> Criteria.where("agentId").is(agentId).and("userId").is(value))
                .orElseGet(() -> Criteria.where("agentId").is(agentId)));
    }

    private AgentStateDO load(String agentId, String userId, String sessionId) {
        return mongo.findById(documentId(agentId, userId, sessionId), AgentStateDO.class);
    }

    private void mutate(AgentStateDO doc, java.util.function.Consumer<AgentStateDO> mutator) {
        AgentStateDO existing = mongo.findById(doc.getId(), AgentStateDO.class);
        if (existing == null) {
            existing = doc;
            existing.setCreateTime(now());
        }
        mutator.accept(existing);
        existing.setUpdateTime(now());
        mongo.save(existing);
    }

    private AgentStateDO document(String agentId, String userId, String sessionId) {
        AgentStateDO doc = new AgentStateDO();
        doc.setId(documentId(agentId, userId, sessionId));
        doc.setAgentId(agentId);
        doc.setUserId(StringUtils.hasText(userId) ? userId : ANONYMOUS_USER);
        doc.setSessionId(sessionId);
        return doc;
    }

    private static String documentId(String agentId, String userId, String sessionId) {
        return agentId + "/" + (StringUtils.hasText(userId) ? userId : ANONYMOUS_USER) + "/" + sessionId;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
