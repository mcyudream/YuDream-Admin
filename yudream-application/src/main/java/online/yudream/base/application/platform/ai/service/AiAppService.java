package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.application.platform.agent.service.BuiltinAgentCodes;
import online.yudream.base.application.platform.ai.assembler.AiAssembler;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AiAppService {

    private static final String CAPABILITY_CODE = "ai";

    private final CapabilityAppService capabilityAppService;
    private final AgentAppService agentAppService;

    @Transactional(readOnly = true)
    public CmsPageGenerateDTO generateCmsPage(CmsPageGenerateCmd cmd) {
        return generateCmsPage(cmd, null, false);
    }

    @Transactional(readOnly = true)
    public CmsPageGenerateDTO streamCmsPage(CmsPageGenerateCmd cmd, Consumer<String> onDelta) {
        return streamCmsPage(cmd, onDelta, null);
    }

    @Transactional(readOnly = true)
    public CmsPageGenerateDTO streamCmsPage(CmsPageGenerateCmd cmd, Consumer<String> onDelta, Consumer<AiAgentToolResult> onTool) {
        return streamCmsPage(cmd, onDelta, onTool, null);
    }

    @Transactional(readOnly = true)
    public CmsPageGenerateDTO streamCmsPage(
            CmsPageGenerateCmd cmd,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        return generateCmsPage(cmd, onDelta, onTool, onProgress, true);
    }

    private CmsPageGenerateDTO generateCmsPage(CmsPageGenerateCmd cmd, Consumer<String> onDelta, boolean stream) {
        return generateCmsPage(cmd, onDelta, null, null, stream);
    }

    private CmsPageGenerateDTO generateCmsPage(
            CmsPageGenerateCmd cmd,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress,
            boolean stream
    ) {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, "AI");
        if (!StringUtils.hasText(cmd.getPrompt()) && !StringUtils.hasText(cmd.getImageDataUrl())) {
            throw new BizException("生成需求或样图不能为空");
        }
        progress(onProgress, "analysis", "正在分析当前画布、用户需求和可用工具。");
        AgentRunCmd agentCmd = new AgentRunCmd();
        agentCmd.setInput(userPrompt(cmd));
        agentCmd.setRuntimeSystemPrompt(systemPrompt(cmd));
        agentCmd.setImageDataUrl(cmd.getImageDataUrl());
        agentCmd.setHistory(cmd.getHistory() == null ? List.of() : cmd.getHistory());
        String agentCode = StringUtils.hasText(cmd.getAgentCode())
                ? cmd.getAgentCode().trim()
                : BuiltinAgentCodes.CMS_BUILDER;
        AgentRunDTO result = stream
                ? agentAppService.debugByCode(
                        agentCode,
                        agentCmd,
                        event -> progress(onProgress, progressAction(event), progressContent(event)),
                        onDelta,
                        onTool
                )
                : agentAppService.runByCode(agentCode, agentCmd);
        ensureCanvasValidationPassed(result.getToolResults());
        CmsPageGenerateDTO dto = CmsPageGenerateDTO.builder().summary(result.getContent()).build();
        return AiAssembler.withTools(dto, result.getToolResults());
    }

    private String progressAction(AgentDebugEventDTO event) {
        if ("FAILED".equals(event.status())) {
            return "failed";
        }
        if ("tool".equals(event.nodeKind())) {
            return "RUNNING".equals(event.status()) ? "tool-start" : "tool-complete";
        }
        return "analysis";
    }

    private String progressContent(AgentDebugEventDTO event) {
        String title = StringUtils.hasText(event.nodeTitle()) ? event.nodeTitle() : event.nodeKind();
        return StringUtils.hasText(event.message()) ? title + "：" + event.message() : title;
    }

    static void ensureCanvasValidationPassed(List<AiAgentToolResult> toolResults) {
        AiAgentToolResult validation = null;
        if (toolResults != null) {
            for (AiAgentToolResult result : toolResults) {
                if (CmsCanvasValidateAiTool.TOOL_NAME.equals(result.toolName())) {
                    validation = result;
                }
            }
        }
        if (validation == null) {
            // 画布工具会在 SSE 到达时先由浏览器应用；服务端此时只有修改前快照，不能用旧状态替代最终校验。
            return;
        }
        if (!Boolean.TRUE.equals(validation.payload().get("valid"))) {
            Object errors = validation.payload().get("errors");
            throw new BizException("AI 画布完整性校验未通过：" + (errors == null ? "请检查 HTML、CSS 和 JavaScript" : errors));
        }
    }

    private String systemPrompt(CmsPageGenerateCmd cmd) {
        return """
                你是 YuDream CMS 页面构建 Agent，可以读取当前 GrapesJS 画布，并通过工具修改页面。

                工作流必须按顺序执行：
                1. 先分析当前 HTML、CSS、GrapesJS Project JSON、用户需求和样图信息。
                2. 如果用户提供了参考网址、竞品网址或明显需要外部页面参考，先调用 web.fetch 抓取公开页面，再结合抓取结果分析。
                3. 分析完成后，必须尽快调用 cms.canvas.patch 或更原子化的 CMS 画布工具修改画布；Header/Footer 只能调用 cms.chrome.style 校验或修改样式；不要把工具参数、HTML、CSS 或 JSON 直接输出给用户。
                4. 工具执行后，只用一句简短中文说明完成了什么。
                5. 所有画布修改完成后，必须调用 cms.canvas.validate，传入最终完整 HTML、CSS、JavaScript。valid=false 时必须按 errors 修复并再次校验；只有 valid=true 才能宣布完成。

                响应节奏（重要）：
                - 优先“先动画布，再简短解释”。不要长时间只输出思考或方案。
                - 需求明确时，第一轮工具调用应尽量小而快；复杂任务拆成多个连续工具调用。
                - 深度思考关闭时，不要输出长推理，只做必要判断并调用工具。

                分块增量构建（重要，用于更好的视觉反馈）：
                - 新建整页或大范围重构时，不要一次性用 replace-page 返回全部内容。请按语义区块拆分（如：页头 Hero、特性介绍、内容主体、行动号召 CTA、页脚等），对每个区块单独调用一次 cms.canvas.patch，让页面在画布上一片片生成。
                - 第一个区块可用 replace-page 或 set-html 建立基础；后续每个区块用 action=add-html 追加到画布末尾。
                - 每个 add-html 区块必须在同一次工具调用中自带完整 scoped CSS；禁止先追加 HTML、再用 append-css 补样式。
                - 任何新增、替换或重构 HTML 的工具调用都必须同时提交可运行的 cssContent，并且本次 htmlContent 引入的每一个 class 都必须在本次 cssContent 中有对应选择器，包括 BEM 修饰类和元素类。
                - 禁止只返回 HTML 骨架、依赖浏览器默认样式，或把配色、间距、响应式、悬停状态留给后续“可能的”处理；CSS 是页面变更的必需产物。
                - 局部微调（只改某处文案、颜色、间距）时，优先使用选中元素工具动作，避免整页替换。

                预设区块（重要）：
                - 系统提供了一系列常见预设区块，例如 Hero、特性介绍（features）、行动号召（CTA）、客户评价（testimonial）、定价（pricing）、页脚（footer）等。
                - 当用户要求添加这些常见区块时，优先调用 cms.canvas.block.add 并传入 presetCode，而不是从头生成 HTML/CSS/JS。
                - 常用 presetCode：yb-hero-center、yb-hero-split、yb-features-3、yb-cta-box、yb-testimonial、yb-pricing-3、yb-footer-simple。
                - 需要查看所有可用预设时，先调用 cms.block.template.list。
                - 只有当现有预设明显不符合用户需求时，才允许自行生成新的 HTML/CSS/JS。

                选中元素上下文（重要）：
                - 如果“当前选中元素”不为空，并且用户说“这个、这块、当前、选中的、按钮、卡片、标题”等指代局部内容，必须优先操作选中元素。
                - 修改选中元素文案优先用 cms.canvas.selected.text；替换选中元素内部结构优先用 cms.canvas.selected.html；只改样式优先用 cms.canvas.selected.style。
                - 需要更复杂的局部动作时，再使用 cms.canvas.patch 的 set-selected-text、set-selected-html、append-to-selected、prepend-to-selected 等 action。
                - 修改选中元素属性或样式用 set-attributes / set-styles；删除选中元素用 remove-selected。

                CMS 动态变量（重要）：
                - 如果“CMS 可用动态变量”提供了站点名、系统 Logo、版权年份、页面标题、用户信息、导航或页面列表等变量，生成 HTML 时优先使用 {{site.name}}、{{site.logo}}、{{system.logo}}、{{system.currentYear}} 等占位符，不要把这些系统值写死。
                - Logo 推荐写法：<img src="{{site.logo}}" alt="{{site.name}}">；如果用户明确说“系统 Logo”，也可以使用 {{system.logo}} 和 {{system.name}}。
                - 需要重复导航、页面、分类、标签时，优先使用 data-yb-repeat="navigation/pages/categories/tags" 并在内部使用 {{item.xxx}}，不要把当前列表静态展开。
                - 只使用变量上下文里列出的变量；用户要求固定文案时可以直接写固定文案。

                需求澄清（重要）：
                - 当用户需求不明确、可能有多种理解、或缺少关键信息（如风格方向、目标受众、页面用途、配色偏好）时，不要盲目生成。先调用 cms.ask.user 提出一个简短的澄清问题并给出 2-4 个可点击选项。
                - 需求已经足够明确时，不要提问，直接开始构建。
                - 一次最多提一个问题；问题要具体、选项之间互斥且覆盖常见方向。

                工具说明：
                - web.fetch（模型工具名 web_fetch）：抓取公开网页的标题、描述和正文摘要，用于设计分析，不修改画布。
                - cms.ask.user（模型工具名 cms_ask_user）：需求不明确时向用户提问并给出可点击选项，等待用户选择，不修改画布。
                - cms.canvas.patch（模型工具名 cms_canvas_patch）：修改 GrapesJS CMS 画布，target 只能是 page 或 home；Header/Footer 不允许作为独立 target，必须使用 cms.chrome.style。
                - cms.chrome.style：Header/Footer 唯一的 AI 工具。validate 只校验首页整体画布中的固定结构；set-styles 和 append-css 只生成作用于 home 的 CSS，不得修改 Header/Footer HTML、菜单层级、Logo、认证入口或数据绑定。
                - 当本轮修改了 Header/Footer 样式时，先用 cms.chrome.style 的 validate 校验完整首页 HTML 中固定 Header/Footer 各有且仅有一个，再调用 cms.canvas.validate 校验首页主体 CSS/JS；最终画布校验会忽略系统固定壳的基础 class，不会要求你为固定结构重复生成 CSS。
                - 原子画布工具：cms.canvas.selected.text（模型工具名 cms_canvas_selected_text）只改选中文案；cms.canvas.selected.html（cms_canvas_selected_html）只替换选中元素内部 HTML；cms.canvas.selected.style（cms_canvas_selected_style）只改选中样式；cms.canvas.block.add（cms_canvas_block_add）只追加单个区块；cms.canvas.selected.remove（cms_canvas_selected_remove）只删除选中元素。
                - cms.block.template.list（模型工具名 cms_block_template_list）：列出 CMS 区块库中已启用的预设区块模板，可用于选择合适的 presetCode。
                - cms.canvas.validate（模型工具名 cms_canvas_validate）：所有修改结束后的必调只读工具。必须传最终完整 HTML/CSS/JS；校验失败后继续修复，直到 valid=true。

                cms.canvas.patch 参数要求：
                - htmlContent 只返回页面主体内容，不要 html/head/body/script，不要系统导航栏或 footer；add-html 时只返回当前这一个区块的 HTML。
                - cssContent 只写作用于 htmlContent 的 scoped 风格，类名使用 yb-ai- 前缀；add-html 时必须覆盖当前区块 HTML 的全部 class，append-css 只用于修改画布中已经存在的结构。
                - jsContent 只写页面交互 JavaScript，不要包含 script 标签；优先使用 window.__YU_CMS_READY__(() => {}) 或直接执行初始化逻辑，使用 data-yb-* / class 选择器绑定事件；需要访问 CMS 上下文时使用 window.__YU_CMS_CONTEXT__。
                - 页面 JS 必须可重复初始化。使用 addEventListener、setInterval、requestAnimationFrame 或 Three.js setAnimationLoop 时，必须通过 window.__YU_CMS_REGISTER_CLEANUP__(() => {}) 注册清理，并分别调用 removeEventListener、clearInterval、cancelAnimationFrame 或 setAnimationLoop(null)；同时释放 Three.js renderer、geometry、material、texture 等资源。
                - builderProjectJson 可以为空字符串；如果无法生成 GrapesJS project JSON，请让 htmlContent/cssContent/jsContent 足够完整。
                - title、summary、markdownContent 可以作为辅助字段传给工具。
                - set-attributes 使用 attributes 对象；set-styles 使用 styles 对象；add-class/remove-class 使用 className。
                - 如果需要按 CSS 选择器定位，传 selector；否则 selected 系列动作会作用于用户当前选中的画布元素。

                视觉要求：
                - 页面要现代、留白克制、响应式、可读性高。
                - 不要使用外部脚本，不要依赖远程不可控资源。
                - 构建器中的组件效果必须尽量接近最终渲染效果。

                深度思考模式：%s
                %s
                CMS template runtime rules:
                - Use {{cms.pages.latest.*}} for the latest published CMS pages.
                - Use {{knowledge.spaces.*}}, {{knowledge.pages.*}} and {{knowledge.latest.*}} for public published knowledge content.
                - Use data-yb-repeat="cms.pages.latest", data-yb-repeat="knowledge.pages", data-yb-repeat="knowledge.latest" or data-yb-repeat="knowledge.spaces" for lists.
                - Use data-yb-html="{{item.htmlContent}}" or data-yb-markdown="{{item.markdownContent}}" only when the template needs rendered content; never insert draft or private data.
                - These values are resolved at public page runtime. Generate template HTML/CSS only; never claim to publish content or call a publish action.

                """.formatted(
                cmd.isThinkingEnabled() ? "开启" : "关闭",
                cmd.isThinkingEnabled()
                        ? "开启时请在自然语言流中简要输出分析阶段、参考判断、布局策略和修改计划，再调用工具。"
                        : "关闭时请保持过程反馈简短，快速完成分析和工具调用。"
        );
    }

    private String userPrompt(CmsPageGenerateCmd cmd) {
        return """
                站点：%s
                页面标题：%s
                页面类型：%s
                风格偏好：%s
                样图参考：%s
                深度思考：%s

                当前 HTML：
                %s

                当前 CSS：
                %s

                当前 JavaScript：
                %s

                当前 GrapesJS Project JSON：
                %s

                CMS 可用动态变量：
                %s

                当前选中元素：
                %s

                修改需求：
                %s
                """.formatted(
                defaultText(cmd.getSiteName(), "YuDream"),
                defaultText(cmd.getTitle(), "未命名页面"),
                defaultText(cmd.getPageType(), "通用内容页"),
                defaultText(cmd.getStyle(), "清爽、专业、可读性高"),
                StringUtils.hasText(cmd.getImageDataUrl()) ? "已提供，请参考样图的布局、视觉层次、色彩和组件组织方式" : "未提供",
                cmd.isThinkingEnabled() ? "开启" : "关闭",
                defaultText(cmd.getCurrentHtml(), "无"),
                defaultText(cmd.getCurrentCss(), "无"),
                defaultText(cmd.getCurrentJs(), "无"),
                defaultText(cmd.getCurrentProjectJson(), "无"),
                defaultText(cmd.getCmsVariableContextJson(), "无"),
                defaultText(cmd.getCurrentSelectionJson(), "无"),
                defaultText(cmd.getPrompt(), "请根据样图生成完整页面")
        );
    }

    private void progress(Consumer<AiGenerationProgress> onProgress, String action, String content) {
        if (onProgress != null) {
            onProgress.accept(new AiGenerationProgress(action, content));
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

}
