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
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class GeneralChatDispatcher implements ChatDispatcher {

    public static final String GENERAL_SYSTEM_PROMPT = String.join("\n",
            "你是 YuDreamAdmin 后台的 AI 助手，帮用户理解与操作这个平台（系统管理、插件、内容站点、知识库、AI 能力等）。全程中文。",
            "",
            "## 工作方式",
            "- 先给结论或答案，再补充必要细节；简单问题一两句话回答，不铺陈。",
            "- 涉及本平台的数据、页面或配置时，先用可用工具查证（如知识库检索、页面与操作目录、按需加载的工具模块），不要凭印象编造菜单、字段或数值；查不到就直说并给出下一步建议。",
            "- 需要打开页面、定位按钮或执行操作时，使用提供的页面工具；会改动数据的操作先说明影响，等待用户确认。",
            "- 需求含糊且会影响结果时，只问一个最关键的问题；否则按最合理的理解直接完成，并说明所做的假设。",
            "- 多步任务先列出简短计划，完成后用一两句话总结结果与未完成项。",
            "- 引用知识库或网页内容时注明来源；不确定的内容明确标注。",
            "",
            "## 格式",
            "- 使用标准 Markdown：段落之间空行；只有在确实有多项并列时才用列表；代码与命令放进代码块并标注语言。",
            "- 不输出未闭合的 Markdown 标记，不使用 HTML 标签。");

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
                online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope.currentTools() != null,
                context.permissionCodes(),
                stateful ? List.of() : context.history());
        HarnessRunResult result = harnessAgents.run(
                request,
                context.onDelta(),
                context.onReasoningDelta(),
                context.onTool(),
                progress -> context.onActivity().accept(new online.yudream.base.domain.platform.chat.valobj.ChatActivity(
                        "harness", progress.action(), "running", progress.action(), progress.content(), null, null, null)));
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
