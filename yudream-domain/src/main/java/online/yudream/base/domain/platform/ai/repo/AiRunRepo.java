package online.yudream.base.domain.platform.ai.repo;

import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiRunRepo {
    AiRun create(AiRun run);
    Optional<AiRun> find(String id);
    List<AiRun> list(Long userId, String sessionId, boolean activeOnly);
    void append(AiRunEvent event, String content, String reasoning);
    boolean finish(String id, AiRunStatus status, Object result, String error);
    void touch(String id);
    void requestCancel(String id);
    void nativeReference(String id, online.yudream.base.domain.platform.ai.valobj.HarnessRunReference reference, boolean resumeSupported);
    boolean reopen(String id, String ownerInstance);
    void nativeProfile(String id, online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile profile);
    void nativeSnapshot(String id, online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot snapshot);
    void frontendContext(String id, java.util.Map<String, Object> context);
    default void invocationContext(String id, online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot context) {
        throw new UnsupportedOperationException("运行仓储不支持持久来源上下文");
    }
    boolean waitForInput(String id, Object result);
    boolean continueWaiting(String id, String ownerInstance);
    void createFrontendRequest(String runId, String toolCallId, String name, java.util.Map<String, Object> arguments, Instant expiresAt);
    boolean acknowledgeFrontend(String runId, String toolCallId, java.util.Map<String, Object> response);
    Optional<java.util.Map<String, Object>> frontendResponse(String runId, String toolCallId);
    void expireFrontend(String runId, String toolCallId);
    List<AiRunEvent> events(String id, long after, int limit);
    List<AiRun> stale(Instant before);
}
