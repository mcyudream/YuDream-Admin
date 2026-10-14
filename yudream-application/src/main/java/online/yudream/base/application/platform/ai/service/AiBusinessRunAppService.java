package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/** 内置 CMS/Agent 工作流也使用相同的运行、事件与恢复出口。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiBusinessRunAppService {
    private final AiRunAppService runs;
    private final AiAppService ai;
    private final AgentAppService agents;
    private final HarnessAgentGateway harness;

    public AiRunDTO cms(CmsPageGenerateCmd cmd) {
        String sessionId = cmd.getSessionId() == null ? "cms-page:" + UUID.randomUUID() : cmd.getSessionId();
        cmd.setSessionId(sessionId);
        return runs.start(cmd.getUserId(), "cms", sessionId, emitter -> {
            emitter.input(cmd.getPrompt());
            try (var ignored = AiUsageExecutionScope.open(cmd.getUserId())) {
                if ("agent".equalsIgnoreCase(cmd.getMode())) {
                    return ai.streamCmsPageAgent(cmd, CmsCanvasClientBridge.external(emitter::frontendRequest),
                            emitter::content, emitter::reasoning, emitter::tool,
                            progress -> emitter.progress(progress.action(), progress));
                }
                return ai.streamCmsPage(cmd, emitter::content, emitter::reasoning, emitter::tool,
                        progress -> emitter.progress(progress.action(), progress));
            }
        }, null);
    }

    public AiRunDTO workspace(Long userId, List<String> permissionCodes, String target, String question, List<AiChatMessage> history,
                               String providerCode, String modelCode) {
        var permissions = permissionCodes == null ? List.<String>of() : List.copyOf(permissionCodes);
        return runs.start(userId, "cms-workspace", "cms-" + target, emitter -> {
            emitter.input(question);
            emitter.allowNativeResume();
            return ai.streamCmsWorkspace(userId, permissions, target, question, List.of(), providerCode, modelCode,
                        emitter::content, emitter::reasoning, emitter::tool,
                        progress -> emitter.progress(progress.action(), progress));
        },
                () -> harness.interrupt(userId, "cms-" + target, "cms-builder"));
    }

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
