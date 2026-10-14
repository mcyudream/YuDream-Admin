package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot;
import online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer;

import java.util.function.Consumer;

/**
 * Harness 级会话代理端口：以 (userId, sessionId) 寻址的有状态 Agent 运行时。
 * 对话上下文由框架 AgentState 管理（跨轮次、自动压缩、长期记忆），
 * 平台消息表仅作为 UI 投影，不再承担上下文裁剪。
 */
public interface HarnessAgentGateway {

    HarnessRunResult run(
            HarnessRunRequest request,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool
    );

    default HarnessRunResult run(HarnessRunRequest request, Consumer<String> onDelta,
                                 Consumer<String> onReasoningDelta, Consumer<AiAgentToolResult> onTool,
                                 Consumer<AiGenerationProgress> onProgress) {
        return run(request, onDelta, onReasoningDelta, onTool);
    }

    /** 用户主动停止指定会话；浏览器订阅断线不应调用此操作。 */
    default boolean interrupt(Long userId, String sessionId, String agentCode) {
        return false;
    }

    default HarnessSessionSnapshot inspect(HarnessRunRequest request) {
        throw new UnsupportedOperationException("当前 Agent 网关不支持持久会话检查");
    }

    /** 持久取消已暂停的外部工具/审批，不伪造执行成功，也不自动重跑副作用。 */
    default HarnessSessionSnapshot cancelPending(HarnessRunRequest request, String turnId) {
        throw new UnsupportedOperationException("当前 Agent 网关不支持取消持久待办");
    }

    default HarnessRunResult respond(HarnessRunRequest request, java.util.List<HarnessInteractionAnswer> answers,
                                     Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                     Consumer<AiAgentToolResult> onTool, Consumer<AiGenerationProgress> onProgress) {
        throw new UnsupportedOperationException("当前 Agent 网关不支持持久待办回复");
    }

    /** 从框架 checkpoint 接续最新的未完成任务，不重复提交用户消息。 */
    default HarnessRunResult resume(HarnessRunRequest request, String turnId, Consumer<String> onDelta,
                                    Consumer<String> onReasoningDelta, Consumer<AiAgentToolResult> onTool,
                                    Consumer<AiGenerationProgress> onProgress) {
        throw new UnsupportedOperationException("当前 Agent 网关不支持任务恢复");
    }
}
