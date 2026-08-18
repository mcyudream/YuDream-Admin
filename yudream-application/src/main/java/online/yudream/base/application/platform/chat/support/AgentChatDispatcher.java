package online.yudream.base.application.platform.chat.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentAttachmentCmd;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentChatDispatcher implements ChatDispatcher {

    private final AgentAppService agentAppService;

    @Override
    public ChatScopeType scopeType() {
        return ChatScopeType.AGENT;
    }

    @Override
    public ChatDispatchResult dispatch(ChatDispatchContext context) {
        if (!StringUtils.hasText(context.agentCode())) {
            throw new BizException("请选择要使用的 Agent 应用");
        }
        AgentRunCmd command = new AgentRunCmd();
        command.setInput(context.question());
        command.setProviderCode(context.providerCode());
        command.setModelCode(context.modelCode());
        command.setHistory(context.history() == null ? List.of() : context.history());
        command.setPermissionCodes(context.permissionCodes() == null ? List.of() : List.copyOf(context.permissionCodes()));
        command.setPermissionContextExplicit(true);
        command.setAttachments(attachments(context));
        List<String> images = imageDataUrls(context);
        command.setImageDataUrl(images.isEmpty() ? null : images.getFirst());
        command.setImageDataUrls(images);
        AgentRunDTO result = agentAppService.debugByCode(
                context.agentCode(),
                command,
                node -> context.onActivity().accept(toActivity(node)),
                context.onDelta(),
                context.onReasoningDelta(),
                context.onTool());
        return ChatDispatchResult.of(result.getContent(), result.getUsage());
    }

    private List<AgentAttachmentCmd> attachments(ChatDispatchContext context) {
        if (context.attachments() == null) {
            return List.of();
        }
        return context.attachments().stream()
                .map(attachment -> new AgentAttachmentCmd(
                        attachment.fileName(),
                        attachment.contentType(),
                        attachment.size(),
                        attachment.dataUrl()))
                .toList();
    }

    private List<String> imageDataUrls(ChatDispatchContext context) {
        if (context.attachments() == null) {
            return List.of();
        }
        return context.attachments().stream()
                .map(online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO::dataUrl)
                .filter(dataUrl -> dataUrl != null && dataUrl.startsWith("data:image/"))
                .toList();
    }

    private ChatActivity toActivity(AgentDebugEventDTO node) {
        if (node == null) {
            return null;
        }
        return new ChatActivity(
                "agent-node",
                node.nodeId(),
                node.status(),
                node.nodeTitle(),
                node.message(),
                null,
                null,
                null);
    }
}
