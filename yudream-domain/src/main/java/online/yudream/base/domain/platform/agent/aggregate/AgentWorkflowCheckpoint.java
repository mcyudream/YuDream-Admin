package online.yudream.base.domain.platform.agent.aggregate;

import online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 持久的宿主业务图状态。供应商配置和旧权限仅在恢复时重新读取，不进入此聚合。 */
public record AgentWorkflowCheckpoint(String runId, Long userId, String applicationCode, String workflowJson,
                                       String applicationSystemPrompt, Map<String, Object> command,
                                       String runKey, AgentWorkflowFrame frame, Map<String, Integer> modelInvocations,
                                       String reasoning, List<AiAgentToolResult> toolResults, AiUsage usage,
                                       String status, Instant updatedAt, java.util.Set<String> nativePhaseRunIds) {
    public AgentWorkflowCheckpoint {
        command = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(command));
        modelInvocations = Map.copyOf(modelInvocations);
        toolResults = List.copyOf(toolResults);
        usage = usage == null ? AiUsage.empty() : usage;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        if (nativePhaseRunIds == null) {
            String initial = frame.suspendedNative() == null ? null : frame.suspendedNative().nativeRunId();
            nativePhaseRunIds = initial == null ? java.util.Set.of() : java.util.Set.of(initial);
        } else nativePhaseRunIds = java.util.Set.copyOf(nativePhaseRunIds);
    }

    public AgentWorkflowCheckpoint(String runId, Long userId, String applicationCode, String workflowJson,
            String applicationSystemPrompt, Map<String, Object> command, String runKey, AgentWorkflowFrame frame,
            Map<String, Integer> modelInvocations, String reasoning, List<AiAgentToolResult> toolResults,
            AiUsage usage, String status, Instant updatedAt) {
        this(runId, userId, applicationCode, workflowJson, applicationSystemPrompt, command, runKey, frame,
                modelInvocations, reasoning, toolResults, usage, status, updatedAt, null);
    }

    /** 同一原生轮次再次等待时保存已经真实完成的阶段；执行 runId 才是阶段身份。 */
    public AgentWorkflowCheckpoint appendNativeSuspension(online.yudream.base.domain.platform.ai.valobj.HarnessRunResult next) {
        var previous = frame.suspendedNative();
        if (!"WAITING".equals(status) || previous == null || !"suspended".equals(next.status())
                || !java.util.Objects.equals(previous.nativeTurnId(), next.nativeTurnId())
                || next.nativeRunId() == null || next.nativeRunId().isBlank()) {
            throw new online.yudream.base.domain.common.exception.BizException("原生阶段与等待中的工作流节点不匹配");
        }
        if (nativePhaseRunIds.contains(next.nativeRunId())) return this;
        var phases = new java.util.HashSet<>(nativePhaseRunIds); phases.add(next.nativeRunId());
        var nodeTools = new java.util.ArrayList<>(previous.toolResults()); nodeTools.addAll(next.toolResults());
        var allTools = new java.util.ArrayList<>(toolResults); allTools.addAll(next.toolResults());
        var merged = new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult(previous.content() + next.content(),
                previous.reasoning() + next.reasoning(), previous.usage().plus(next.usage()), nodeTools,
                next.pendingConfirmations(), next.nativeTurnId(), next.nativeRunId(), next.status(), next.pendingInteractions());
        var nextFrame = new AgentWorkflowFrame(frame.cursor(), frame.pausedNodeId(), frame.input(), frame.activatedNodeIds(),
                frame.executedNodeIds(), frame.nodeOutputs(), frame.variables(), merged);
        return new AgentWorkflowCheckpoint(runId, userId, applicationCode, workflowJson, applicationSystemPrompt, command,
                runKey, nextFrame, modelInvocations, reasoning + next.reasoning(), allTools, usage.plus(next.usage()),
                "WAITING", Instant.now(), phases);
    }
}
