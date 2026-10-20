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
 * cms-builder=CMS 站点工作区、workflow-<appCode>=Agent 应用工作区；
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
            // CMS builder：文件工具 + chrome 样式（页面构建的核心工具面）
            case "cms-builder" -> List.of(
                    "read_file", "write_file", "edit_file", "list_files", "grep_files", "glob_files",
                    "cms_chrome_style");
            // CMS 画布：工具面由本次运行的客户端画布工具集定义，这里不再额外设限。
            case "cms-studio" -> List.of("cms_studio_read", "cms_studio_apply", "cms_studio_validate");
            case "cms-canvas" -> List.of();
            default -> List.of();
        };
    }

    private String resolveAgentCode(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return "general";
        }
        String normalized = agentCode.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("cms")) {
            return "cms-builder";
        }
        return normalized.equals("general") || normalized.equals("cms-builder") || normalized.equals("cms-canvas") || normalized.equals("cms-studio")
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
            return "你是 YuDreamAdmin 站点设计器 AST 草稿助手。仅能使用 cms.studio.read、cms.studio.apply、cms.studio.validate，"
                    + "不生成或执行 HTML/脚本，不访问文件，不保存、不发布。先说明拟修改页面、主题或资产范围。"
                    + "每轮先 read，分页读取直到获得所需节点；工具参数绑定本轮 contextKey/documentId/pageId。"
                    + "apply 必须使用最近 read 的 revision 作为 expectedRevision。所有修改集中到一个 batch 命令，禁止嵌套 batch，一轮一次原子应用、一次撤销。"
                    + "命令格式：set-text{nodeId,text}；update-node{nodeId,patch}；set-style{nodeId,styles,breakpoint?:desktop|tablet|mobile}；"
                    + "insert{target:{pageId}|{region:header|footer}|{parentId},node,index?}；remove/move/duplicate；"
                    + "set-theme{tokens?,header?,footer?}；add-page{page}；update-page{pageId,patch}；remove-page{pageId}；set-assets{assets}，均含 type。"
                    + "Node={id,type,tag,text?,attributes,styles,responsive?,children}；有children的节点不能set-text。"
                    + "context/revision 过期或工具失败必须报告并重新读取，禁止静默覆盖或声称修改成功。"
                    + "apply 后必须 validate 当前真实文档，valid=true 才总结完成。用户手工保存与发布。全程中文。";
        }
        if ("cms-canvas".equals(agentCode)) {
            return online.yudream.base.application.platform.cms.service.CmsCanvasAgentPrompt.systemPrompt(
                    siteName(),
                    online.yudream.base.application.platform.cms.service.CmsCanvasAgentPrompt.templateDescription(null),
                    false);
        }
        if ("cms-builder".equals(agentCode)) {
            return "你是 YuDreamAdmin CMS 静态站点工作区的构建代理。工作区是多文件静态站点："
                    + "pages/ 下每页一个 HTML（pages/index.html 为首页），assets/ 存放样式、脚本与资产。"
                    + "页面可包含 script；数据互通推荐平台运行时库（预览与公开输出自动注入）。"
                    + "数据注入只用 data-yb-for/repeat、data-yb-if、data-yb-limit、data-yb-html/markdown "
                    + "与 {{dotted.path}} 变量。CSS 只用 yb-ai- 前缀类选择器写入 assets/css/site.css。"
                    + "先读后写、小步修改；发布由用户在界面触发。全程使用中文。";
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
