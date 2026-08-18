package online.yudream.base.application.platform.chat.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeneralChatDispatcher implements ChatDispatcher {

    private final AiGenerationGateway generationGateway;
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
        List<AiChatMessage> history = context.history() == null ? List.of() : context.history();
        List<String> imageDataUrls = imageDataUrls(context);
        if (!imageDataUrls.isEmpty()) {
            throw new BizException("通用聊天不直接处理图片，请切换到支持视觉能力的 Agent 应用后再发送图片");
        }
        AiGenerationRequest request = new AiGenerationRequest(
                "你是 YuDreamAdmin 的 AI 助手，请使用中文清晰、准确地回答用户问题。"
                        + "正文必须使用标准 Markdown：段落之间保留空行，列表、标题、引用和代码块使用规范语法；"
                        + "不要输出未闭合的 Markdown 标记。",
                context.question(),
                imageDataUrls,
                providerCode,
                modelCode,
                config,
                history,
                false,
                AiToolMode.NONE);
        AiGenerationResult result = generationGateway.generateStream(
                request,
                context.onDelta(),
                context.onReasoningDelta(),
                context.onTool(),
                progress -> context.onActivity().accept(new ChatActivity(
                        "chat-progress",
                        progress.action(),
                        "stream-complete".equals(progress.action()) || "complete".equals(progress.action()) ? "completed" : "running",
                        progressTitle(progress.action()),
                        progress.content(),
                        null,
                        null,
                        null)));
        return ChatDispatchResult.of(result.summary(), result.usage());
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

    private String progressTitle(String action) {
        return switch (action == null ? "" : action) {
            case "request" -> "准备模型请求";
            case "subscribed" -> "连接模型";
            case "first-delta" -> "正在生成";
            case "stream-complete", "complete" -> "生成完成";
            default -> "AI 生成";
        };
    }
}
