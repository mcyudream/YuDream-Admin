package online.yudream.base.application.platform.chat.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 通用聊天调度：走 Harness 会话代理（框架管理上下文/压缩/长期记忆）。
 * 有状态运行（send）不传历史——上下文由 AgentState 按 (userId, sessionId) 恢复；
 * 一次性运行（streamOnce）显式注入调用方历史。
 */
@Component
@RequiredArgsConstructor
public class GeneralChatDispatcher implements ChatDispatcher {

    public static final String GENERAL_SYSTEM_PROMPT =
            "你是 YuDreamAdmin 的 AI 助手，请使用中文清晰、准确地回答用户问题。"
                    + "正文必须使用标准 Markdown：段落之间保留空行，列表、标题、引用和代码块使用规范语法；"
                    + "不要输出未闭合的 Markdown 标记。";

    private final HarnessAgentGateway harnessAgents;
    private final CapabilityModuleRepo capabilityModuleRepo;

    @Override
    public ChatScopeType scopeType() {
        return ChatScopeType.GENERAL;
    }

    @Override
    public ChatDispatchResult dispatch(ChatDispatchContext context) {
        Map<String, String> config = aiConfig();
        String providerCode = StringUtils.hasText(context.providerCode())
                ? context.providerCode()
                : config.get("defaultProvider");
        String modelCode = StringUtils.hasText(context.modelCode())
                ? context.modelCode()
                : config.get("defaultModel");
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(modelCode)) {
            throw new BizException("请选择可用的 AI 模型");
        }
        boolean stateful = StringUtils.hasText(context.sessionId());
        HarnessRunRequest request = new HarnessRunRequest(
                context.userId(),
                stateful ? "chat-" + context.sessionId() : null,
                "general",
                GENERAL_SYSTEM_PROMPT,
                context.question(),
                imageDataUrls(context),
                providerCode,
                modelCode,
                config,
                false,
                context.permissionCodes(),
                stateful ? List.of() : context.history());
        HarnessRunResult result = harnessAgents.run(
                request,
                context.onDelta(),
                context.onReasoningDelta(),
                context.onTool());
        return ChatDispatchResult.of(result.content(), result.usage());
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

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用"));
    }
}
