package online.yudream.base.domain.platform.ai.aggregate;

import lombok.Data;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;

import java.time.Instant;
import java.util.UUID;

/** Harness 运行的用户界面投影；模型上下文与调度仍由 AgentScope 管理。 */
@Data
public class AiRun {
    private String id;
    private Long userId;
    private String sessionId;
    private String module;
    private String ownerInstance;
    private boolean cancelRequested;
    private online.yudream.base.domain.platform.ai.valobj.HarnessRunReference nativeReference;
    private boolean nativeResumeSupported;
    private online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile nativeProfile;
    private online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot nativeSnapshot;
    private java.util.Map<String, Object> frontendContext;
    private online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot invocationContext;
    private AiRunStatus status;
    private long cursor;
    private String content = "";
    private String reasoning = "";
    private Object result;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;

    public static AiRun start(Long userId, String sessionId, String module, String ownerInstance) {
        if (userId == null) throw new BizException("当前用户未登录");
        AiRun run = new AiRun();
        run.id = UUID.randomUUID().toString();
        run.userId = userId;
        run.sessionId = sessionId;
        run.module = module;
        run.ownerInstance = ownerInstance;
        run.status = AiRunStatus.RUNNING;
        run.createdAt = Instant.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    public void belongsTo(Long userId) {
        if (userId == null || !userId.equals(this.userId)) throw new BizException("无权访问该 AI 运行");
    }
}
