package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;

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
}
