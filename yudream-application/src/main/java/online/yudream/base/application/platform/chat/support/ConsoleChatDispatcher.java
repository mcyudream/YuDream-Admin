package online.yudream.base.application.platform.chat.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 控制台调度：Harness 会话直连（会话即工作区实例）。
 * agentCode 决定 profile 与工作区：general=通用助手（无文件工具）、
 * cms-studio=站点设计器、workflow-<appCode>=Agent 应用工作区；
 * sessionId 与 ChatSession 1:1（console-<id>），跨轮次上下文由框架 AgentState 管理，
 * 消息表仅作 UI 投影。
 */
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ConsoleChatDispatcher implements ChatDispatcher {

    private final HarnessAgentGateway harnessAgents;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final online.yudream.base.application.system.setting.service.SettingAppService settingAppService;
    private online.yudream.base.application.platform.ai.service.CmsStudioClientTools studioTools;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void studioTools(online.yudream.base.application.platform.ai.service.CmsStudioClientTools tools) { studioTools = tools; }

    @Override
    public ChatScopeType scopeType() {
        return ChatScopeType.CONSOLE;
    }

    @Override
    public ChatDispatchResult dispatch(ChatDispatchContext context) {
        String agentCode = resolveAgentCode(context.agentCode());
        boolean stateful = StringUtils.hasText(context.sessionId());
        List<String> allowed = allowedTools(agentCode);
        List<String> permissionCodes = context.permissionCodes() == null ? List.of() : List.copyOf(context.permissionCodes());
        if ("cms-studio".equals(agentCode)) {
            if (studioTools == null) throw new online.yudream.base.domain.common.exception.BizException("站点设计器 AI 工具不可用");
            studioTools.ensureAccess(permissionCodes);
            studioTools.contextKey(context.userId(), context.sessionId());
        }
        HarnessRunRequest request = new HarnessRunRequest(
                context.userId(),
                stateful ? "console-" + context.sessionId() : null,
                agentCode,
                consoleSystemPrompt(agentCode),
                context.question(),
                imageDataUrls(context),
                context.providerCode(),
                context.modelCode(),
                aiConfig(),
                true,
                permissionCodes,
                stateful ? List.of() : context.history(),
                allowed,
                context.confirmDecisions());
        HarnessRunResult result = harnessAgents.run(
                request,
                context.onDelta(),
                context.onReasoningDelta(),
                context.onTool(),
                progress -> context.onActivity().accept(new online.yudream.base.domain.platform.chat.valobj.ChatActivity(
                        "harness", progress.action(), "running", progress.action(), progress.content(), null, null, null)));
        return ChatDispatchResult.of(result.content(), result.usage());
    }

    private List<String> allowedTools(String agentCode) {
        return switch (agentCode) {
            // CMS 画布：工具面由本次运行的客户端画布工具集定义，这里不再额外设限。
            case "cms-studio" -> online.yudream.base.application.platform.ai.service.CmsStudioClientTools.NAMES.stream().map(name -> name.replace('.', '_')).toList();
            case "cms-canvas" -> List.of();
            default -> List.of();
        };
    }

    private String resolveAgentCode(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return "general";
        }
        String normalized = agentCode.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("cms") || normalized.equals("cms-builder")) {
            throw new online.yudream.base.domain.common.exception.BizException("旧站点工作区已移除，请在网站工作台使用 AI 设计助手");
        }
        return normalized.equals("general") || normalized.equals("cms-canvas") || normalized.equals("cms-studio")
                || normalized.startsWith("workflow-")
                ? normalized
                : "general";
    }

    private List<String> imageDataUrls(ChatDispatchContext context) {
        if (context.attachments() == null) {
            return List.of();
        }
        return context.attachments().stream()
                .map(online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO::dataUrl)
                .filter(url -> url != null && url.startsWith("data:image/"))
                .toList();
    }

    private String consoleSystemPrompt(String agentCode) {
        if ("cms-studio".equals(agentCode)) {
            return String.join("\n",
                    "你是 YuDreamAdmin 网站工作台的设计助手，直接修改设计器里的站点草稿（结构化 AST，不是 HTML）。全程中文。",
                    "",
                    "## 工作循环",
                    "1. 理解：消息末尾附带当前页面与选中节点（绑定数据只作参考，以工具读取为准）。需求不清时先问一个关键问题。",
                    "2. 观察：用 cms.studio.outline 看结构，cms.studio.find 按文本/样式类/绑定定位，cms.studio.inspect 取要改节点的原值，cms.studio.theme 看样式类、站点内容字段、导航与页面设置；不要逐页 read 整个文档。",
                    "3. 计划：多步修改先用一两句话说明要改的元素/样式类/内容字段。",
                    "4. 修改：本轮全部改动放进一个 {type:batch} 交给 cms.studio.apply（expectedRevision 用本轮观察到的 revision），用户确认后才写入草稿；被拒绝或陈旧时重新观察，不要重复提交同一内容。",
                    "5. 检查：apply 后调用 cms.studio.validate（须 valid=true）与 cms.studio.check（手机/桌面渲染问题）；check 有问题时说明并建议下一轮修正。",
                    "6. 总结：列出改动与检查结果；保存草稿与发布由用户手动完成。",
                    "",
                    "## 规则",
                    "- 用户要改文字、图片、横幅、卡片、介绍等可编辑内容时，先 theme(section=content)：已有对应字段就用 set-content 改值，不改页面结构。",
                    "- 复用主题样式类；同类元素用 set-class 新建或修改样式类，不要逐个节点写内联样式；数据列表条目（data-yd-item）的样式只能写在类上。",
                    "- 保持数据绑定（data-yd-*）与站点功能，不要把动态数据改成写死的文字；业务数据页由插件提供，不在主题里复制。",
                    "- 只有 apply 能改草稿；工具失败或上下文/修订过期必须如实报告并重新观察，禁止声称未发生的修改。",
                    "- 不生成或执行脚本，不访问文件，不保存、不发布。",
                    "- 不确定命令格式、绑定属性、内容模型、站点功能或外壳页规则时，先调 cms.studio.guide 对应 topic（commands/styles/bindings/content/effects/pages/checklist）。");
        }
        if ("cms-canvas".equals(agentCode)) {
            return online.yudream.base.application.platform.cms.service.CmsCanvasAgentPrompt.systemPrompt(
                    siteName(),
                    online.yudream.base.application.platform.cms.service.CmsCanvasAgentPrompt.templateDescription(null),
                    false);
        }
        if (agentCode.startsWith("workflow-")) {
            return "你是 YuDreamAdmin 的 Agent 应用工作区助手，围绕应用工作区文件与用户协作。"
                    + "正文使用标准 Markdown。全程使用中文。";
        }
        return "你是 YuDreamAdmin 的 AI 控制台助手。正文使用标准 Markdown，"
                + "段落之间保留空行，列表、标题、引用和代码块使用规范语法。全程使用中文。";
    }

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException("AI 能力未启用"));
    }

    /** 站点名用于画布 Agent 提示词；读取失败时回落中性名称，不阻断会话。 */
    private String siteName() {
        try {
            String name = settingAppService.siteSettings().getSiteName();
            return StringUtils.hasText(name) ? name : "当前站点";
        }
        catch (Exception ignored) {
            return "当前站点";
        }
    }
}
