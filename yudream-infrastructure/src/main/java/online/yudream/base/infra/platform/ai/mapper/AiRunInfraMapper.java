package online.yudream.base.infra.platform.ai.mapper;

import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import online.yudream.base.infra.platform.ai.dataobj.AiRunDO;
import online.yudream.base.infra.platform.ai.dataobj.AiRunEventDO;

public final class AiRunInfraMapper {
    private AiRunInfraMapper() { }
    public static AiRunDO run(AiRun run) {
        AiRunDO out = new AiRunDO();
        out.setId(run.getId()); out.setUserId(run.getUserId()); out.setSessionId(run.getSessionId());
        if (run.getSessionId() != null && run.getStatus() == online.yudream.base.domain.platform.ai.enumerate.AiRunStatus.RUNNING)
            out.setActiveKey(run.getUserId() + ":" + run.getModule() + ":" + run.getSessionId());
        out.setModule(run.getModule()); out.setOwnerInstance(run.getOwnerInstance()); out.setStatus(run.getStatus());
        out.setCancelRequested(run.isCancelRequested());
        out.setNativeReference(run.getNativeReference()); out.setNativeResumeSupported(run.isNativeResumeSupported());
        out.setNativeProfile(run.getNativeProfile());
        out.setNativeSnapshot(run.getNativeSnapshot()); out.setFrontendContext(run.getFrontendContext());
        out.setInvocationContext(run.getInvocationContext());
        out.setCursor(run.getCursor()); out.setContent(run.getContent()); out.setReasoning(run.getReasoning());
        out.setResult(run.getResult()); out.setError(run.getError());
        out.setCreatedAt(run.getCreatedAt()); out.setUpdatedAt(run.getUpdatedAt());
        return out;
    }
    public static AiRun run(AiRunDO run) {
        if (run == null) return null;
        AiRun out = new AiRun();
        out.setId(run.getId()); out.setUserId(run.getUserId()); out.setSessionId(run.getSessionId());
        out.setModule(run.getModule()); out.setOwnerInstance(run.getOwnerInstance()); out.setStatus(run.getStatus());
        out.setCancelRequested(run.isCancelRequested());
        out.setNativeReference(run.getNativeReference()); out.setNativeResumeSupported(run.isNativeResumeSupported());
        out.setNativeProfile(run.getNativeProfile());
        out.setNativeSnapshot(run.getNativeSnapshot()); out.setFrontendContext(run.getFrontendContext());
        out.setInvocationContext(run.getInvocationContext());
        out.setCursor(run.getCursor()); out.setContent(run.getContent()); out.setReasoning(run.getReasoning());
        out.setResult(run.getResult()); out.setError(run.getError());
        out.setCreatedAt(run.getCreatedAt()); out.setUpdatedAt(run.getUpdatedAt());
        return out;
    }
    public static AiRunEventDO event(AiRunEvent event) {
        AiRunEventDO out = new AiRunEventDO();
        out.setId(event.runId() + ":" + event.cursor()); out.setRunId(event.runId()); out.setCursor(event.cursor());
        out.setEvent(event.event()); out.setAction(event.action()); out.setModule(event.module());
        out.setTraceId(event.traceId()); out.setTimestamp(event.timestamp()); out.setPayload(event.payload());
        return out;
    }
    public static AiRunEvent event(AiRunEventDO event) {
        return new AiRunEvent(event.getRunId(), event.getCursor(), event.getEvent(), event.getAction(),
                event.getModule(), event.getTraceId(), event.getTimestamp(), event.getPayload());
    }
}
