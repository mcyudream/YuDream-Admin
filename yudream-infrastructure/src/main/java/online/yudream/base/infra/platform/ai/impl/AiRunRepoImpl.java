package online.yudream.base.infra.platform.ai.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;
import online.yudream.base.domain.platform.ai.repo.AiRunRepo;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import online.yudream.base.infra.platform.ai.dataobj.AiRunDO;
import online.yudream.base.infra.platform.ai.dataobj.AiRunEventDO;
import online.yudream.base.infra.platform.ai.dataobj.AiFrontendRequestDO;
import online.yudream.base.infra.platform.ai.mapper.AiRunInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AiRunRepoImpl implements AiRunRepo {
    private final MongoTemplate mongo;
    private volatile boolean indexesReady;
    private synchronized void ensureIndexes() {
        if (indexesReady) return;
        mongo.indexOps(AiRunDO.class).ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                .on("activeKey", Sort.Direction.ASC).unique().sparse());
        mongo.indexOps(AiRunEventDO.class).ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                .on("runId", Sort.Direction.ASC).on("cursor", Sort.Direction.ASC).unique().named("runCursor"));
        indexesReady = true;
    }
    public AiRun create(AiRun run) {
        ensureIndexes();
        try { return AiRunInfraMapper.run(mongo.insert(AiRunInfraMapper.run(run))); }
        catch (org.springframework.dao.DuplicateKeyException error) {
            throw new online.yudream.base.domain.common.exception.BizException("当前会话正在运行，请等待结束或明确取消后重试");
        }
    }
    public Optional<AiRun> find(String id) { return Optional.ofNullable(AiRunInfraMapper.run(mongo.findById(id, AiRunDO.class))); }
    public List<AiRun> list(Long userId, String sessionId, boolean activeOnly) {
        Criteria criteria = Criteria.where("userId").is(userId);
        if (sessionId != null) criteria.and("sessionId").is(sessionId);
        if (activeOnly) criteria.and("status").in(AiRunStatus.RUNNING, AiRunStatus.WAITING);
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        if (!activeOnly) query.limit(100);
        var history = mongo.find(query, AiRunDO.class);
        if (activeOnly) return history.stream().map(AiRunInfraMapper::run).toList();
        // 长时间等待的任务不能被后来的一百条终态运行从历史页挤走。
        var merged = new java.util.LinkedHashMap<String, AiRunDO>();
        mongo.find(Query.query(criteria.and("status").in(AiRunStatus.RUNNING, AiRunStatus.WAITING)), AiRunDO.class)
                .forEach(run -> merged.put(run.getId(), run));
        history.forEach(run -> merged.putIfAbsent(run.getId(), run));
        return merged.values().stream().sorted(java.util.Comparator.comparing(AiRunDO::getCreatedAt).reversed())
                .map(AiRunInfraMapper::run).toList();
    }
    public void append(AiRunEvent event, String content, String reasoning) {
        // 先持久事件，再推进快照游标；读者不会看到尚未落盘的序号。
        try {
            mongo.insert(AiRunInfraMapper.event(event));
            Update update = new Update().set("cursor", event.cursor()).set("updatedAt", Instant.now());
            if (content != null) update.set("content", content);
            if (reasoning != null) update.set("reasoning", reasoning);
            long modified = mongo.updateFirst(Query.query(Criteria.where("id").is(event.runId())
                            .and("cursor").is(event.cursor() - 1).and("status").in(AiRunStatus.RUNNING, AiRunStatus.WAITING)), update, AiRunDO.class)
                    .getModifiedCount();
            if (modified != 1) throw new IllegalStateException("AI 运行日志游标冲突");
        } catch (RuntimeException error) {
            // 两写之间失败时保留已提交前缀并停止运行，绝不重发撞序号或暴露孤立事件。
            finish(event.runId(), AiRunStatus.FAILED, null, "运行日志保存失败，已提交内容仍然保留");
            throw error;
        }
    }
    public boolean finish(String id, AiRunStatus status, Object result, String error) {
        return mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").in(AiRunStatus.RUNNING, AiRunStatus.WAITING)),
                new Update().set("status", status).set("result", result).set("error", error)
                        .unset("activeKey").set("updatedAt", Instant.now()), AiRunDO.class).getModifiedCount() > 0;
    }
    public void touch(String id) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").in(AiRunStatus.RUNNING, AiRunStatus.WAITING)),
                new Update().set("updatedAt", Instant.now()), AiRunDO.class);
    }
    public void requestCancel(String id) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(AiRunStatus.RUNNING)),
                new Update().set("cancelRequested", true), AiRunDO.class);
    }
    public void nativeReference(String id, online.yudream.base.domain.platform.ai.valobj.HarnessRunReference reference, boolean resumeSupported) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(AiRunStatus.RUNNING)),
                new Update().set("nativeReference", reference).set("nativeResumeSupported", resumeSupported), AiRunDO.class);
    }
    public boolean reopen(String id, String ownerInstance) {
        AiRunDO run = mongo.findById(id, AiRunDO.class);
        if (run == null) return false;
        return mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(AiRunStatus.FAILED)
                        .and("nativeResumeSupported").is(true)),
                new Update().set("status", AiRunStatus.RUNNING).set("ownerInstance", ownerInstance)
                        .set("activeKey", run.getUserId() + ":" + run.getModule() + ":" + run.getSessionId())
                        .set("cancelRequested", false).unset("error").set("updatedAt", Instant.now()), AiRunDO.class).getModifiedCount() == 1;
    }
    public void nativeProfile(String id, online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile profile) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(AiRunStatus.RUNNING)),
                new Update().set("nativeProfile", profile), AiRunDO.class);
    }
    public void createFrontendRequest(String runId, String toolCallId, String name,
                                      java.util.Map<String, Object> arguments, Instant expiresAt) {
        AiFrontendRequestDO request = new AiFrontendRequestDO();
        request.setId(runId + ":" + toolCallId); request.setRunId(runId); request.setToolCallId(toolCallId);
        request.setName(name); request.setArguments(arguments); request.setExpiresAt(expiresAt); request.setStatus("WAITING");
        mongo.upsert(Query.query(Criteria.where("id").is(request.getId())),
                new Update().setOnInsert("runId", runId).setOnInsert("toolCallId", toolCallId).setOnInsert("name", name)
                        .setOnInsert("arguments", arguments).setOnInsert("expiresAt", expiresAt).setOnInsert("status", "WAITING"), AiFrontendRequestDO.class);
    }
    public boolean acknowledgeFrontend(String runId, String toolCallId, java.util.Map<String, Object> response) {
        return mongo.updateFirst(Query.query(Criteria.where("id").is(runId + ":" + toolCallId)
                        .and("status").is("WAITING").orOperator(Criteria.where("expiresAt").gt(Instant.now()), Criteria.where("expiresAt").is(null))),
                new Update().set("status", "RESPONDED").set("response", response), AiFrontendRequestDO.class).getModifiedCount() > 0;
    }
    public Optional<java.util.Map<String, Object>> frontendResponse(String runId, String toolCallId) {
        AiFrontendRequestDO request = mongo.findById(runId + ":" + toolCallId, AiFrontendRequestDO.class);
        return request == null || !"RESPONDED".equals(request.getStatus()) ? Optional.empty() : Optional.of(request.getResponse());
    }
    public void expireFrontend(String runId, String toolCallId) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(runId + ":" + toolCallId).and("status").is("WAITING")),
                new Update().set("status", "EXPIRED"), AiFrontendRequestDO.class);
    }
    public void nativeSnapshot(String id, online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot snapshot) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().set("nativeSnapshot", snapshot), AiRunDO.class);
    }
    public void frontendContext(String id, java.util.Map<String, Object> context) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().set("frontendContext", context), AiRunDO.class);
    }
    public void invocationContext(String id, online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot context) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().set("invocationContext", context), AiRunDO.class);
    }
    public boolean waitForInput(String id, Object result) {
        return mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(AiRunStatus.RUNNING)),
                new Update().set("status", AiRunStatus.WAITING).set("result", result).set("updatedAt", Instant.now()), AiRunDO.class).getModifiedCount() == 1;
    }
    public boolean continueWaiting(String id, String instance) {
        return mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(AiRunStatus.WAITING)),
                new Update().set("status", AiRunStatus.RUNNING).set("ownerInstance", instance).set("updatedAt", Instant.now()), AiRunDO.class).getModifiedCount() == 1;
    }
    public List<AiRunEvent> events(String id, long after, int limit) {
        AiRunDO head = mongo.findById(id, AiRunDO.class);
        if (head == null || after >= head.getCursor()) return List.of();
        return mongo.find(Query.query(Criteria.where("runId").is(id).and("cursor").gt(after).lte(head.getCursor()))
                        .with(Sort.by(Sort.Direction.ASC, "cursor")).limit(limit), AiRunEventDO.class)
                .stream().map(AiRunInfraMapper::event).toList();
    }
    public List<AiRun> stale(Instant before) {
        return mongo.find(Query.query(Criteria.where("status").is(AiRunStatus.RUNNING).and("updatedAt").lt(before)), AiRunDO.class)
                .stream().map(AiRunInfraMapper::run).toList();
    }
}
