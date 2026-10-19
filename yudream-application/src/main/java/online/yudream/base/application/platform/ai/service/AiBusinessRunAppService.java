package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import org.springframework.stereotype.Service;

/**
 * 内置 Agent 工作流运行出口。CMS 页面生成与画布构建不再有独立入口：
 * 统一走 AI 助手网关（chat 会话 + console profile 的 cms-canvas / cms-builder）。
 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiBusinessRunAppService {
    private final AiRunAppService runs;
    private final AgentAppService agents;

    public AiRunDTO agent(AgentRunCmd cmd) {
        return agent(cmd, "agent:" + cmd.getApplicationId());
    }

    public AiRunDTO debug(AgentRunCmd cmd) {
        return agent(cmd, "agent-debug:" + cmd.getApplicationId());
    }

    private AiRunDTO agent(AgentRunCmd cmd, String sessionId) {
        cmd.setSessionId(sessionId);
        return runs.start(cmd.getUserId(), "agent", sessionId, emitter -> {
            emitter.input(cmd.getInput());
            try (var ignored = AiUsageExecutionScope.open(cmd.getUserId())) {
                return agents.debug(cmd, node -> emitter.progress(node.nodeId(), node),
                        emitter::content, emitter::reasoning, emitter::tool);
            }
        }, null);
    }
}
