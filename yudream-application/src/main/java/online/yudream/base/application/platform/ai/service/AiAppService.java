package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.assembler.AiAssembler;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AiAppService {

    private static final String CAPABILITY_CODE = "ai";

    private final CapabilityAppService capabilityAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiGenerationGateway> aiGenerationGatewayProvider;
    private final ObjectProvider<AiAgentTool> aiAgentToolProvider;

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
        Map<String, String> config = capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElse(Map.of());
        String modelCode = StringUtils.hasText(cmd.getModelCode()) ? cmd.getModelCode() : cmd.getModel();
        AiGenerationRequest request = new AiGenerationRequest(systemPrompt(cmd), userPrompt(cmd), cmd.getImageDataUrl(), cmd.getProviderCode(), modelCode, config, cmd.getHistory());
        AiGenerationGateway gateway = aiGenerationGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new BizException("AI 能力未在当前项目配置中启用");
        }
        progress(onProgress, "analysis", "正在分析当前画布、用户需求和可用工具。");
        AiGenerationResult result = stream ? gateway.generateStream(request, onDelta, onTool, onProgress) : gateway.generate(request);
        CmsPageGenerateDTO dto = AiAssembler.toDTO(result);
        boolean hasNativeToolResults = result.toolResults() != null && !result.toolResults().isEmpty();
        List<AiAgentToolResult> toolResults = hasNativeToolResults ? result.toolResults() : executeToolCalls(result, dto);
        if (onTool != null && (!stream || !hasNativeToolResults)) {
            toolResults.forEach(onTool);
        }
        return AiAssembler.withTools(dto, toolResults);
    }

    private String systemPrompt(CmsPageGenerateCmd cmd) {
        return """
                你是 YuDream CMS 页面构建 Agent，可以读取当前 GrapesJS 画布，并通过工具修改页面。

                工作流必须按顺序执行：
                1. 先分析当前 HTML、CSS、GrapesJS Project JSON、用户需求和样图信息。
                2. 如果用户提供了参考网址、竞品网址或明显需要外部页面参考，先调用 web.fetch 抓取公开页面，再结合抓取结果分析。
                3. 分析完成后，必须尽快调用 cms.canvas.patch 或更原子化的 CMS 画布工具修改画布；不要把工具参数、HTML、CSS 或 JSON 直接输出给用户。
                4. 工具执行后，只用一句简短中文说明完成了什么。

                响应节奏（重要）：
                - 优先“先动画布，再简短解释”。不要长时间只输出思考或方案。
                - 需求明确时，第一轮工具调用应尽量小而快；复杂任务拆成多个连续工具调用。
                - 深度思考关闭时，不要输出长推理，只做必要判断并调用工具。

                分块增量构建（重要，用于更好的视觉反馈）：
                - 新建整页或大范围重构时，不要一次性用 replace-page 返回全部内容。请按语义区块拆分（如：页头 Hero、特性介绍、内容主体、行动号召 CTA、页脚等），对每个区块单独调用一次 cms.canvas.patch，让页面在画布上一片片生成。
                - 第一个区块可用 replace-page 或 set-html 建立基础；后续每个区块用 action=add-html 追加到画布末尾。
                - 每个 add-html 区块要自带该区块所需的 scoped 样式，或紧随其后用 action=append-css 追加对应样式，避免样式滞后于结构。
                - 局部微调（只改某处文案、颜色、间距）时，优先使用选中元素工具动作，避免整页替换。

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
                - cms.canvas.patch（模型工具名 cms_canvas_patch）：修改 GrapesJS CMS 画布，action 支持 replace-page、set-html、set-css、append-css、set-js、append-js、load-project、add-html、remove-selector、replace-selected、set-selected-html、append-to-selected、prepend-to-selected、set-selected-text、set-attributes、set-styles、add-class、remove-class、remove-selected。
                - 原子画布工具：cms.canvas.selected.text（模型工具名 cms_canvas_selected_text）只改选中文案；cms.canvas.selected.html（cms_canvas_selected_html）只替换选中元素内部 HTML；cms.canvas.selected.style（cms_canvas_selected_style）只改选中样式；cms.canvas.block.add（cms_canvas_block_add）只追加单个区块；cms.canvas.selected.remove（cms_canvas_selected_remove）只删除选中元素。

                cms.canvas.patch 参数要求：
                - htmlContent 只返回页面主体内容，不要 html/head/body/script，不要系统导航栏或 footer；add-html 时只返回当前这一个区块的 HTML。
                - cssContent 只写作用于 htmlContent 的 scoped 风格，类名使用 yb-ai- 前缀；append-css 时只返回本次要新增的样式片段。
                - jsContent 只写页面交互 JavaScript，不要包含 script 标签；优先使用 window.__YU_CMS_READY__(() => {}) 或直接执行初始化逻辑，使用 data-yb-* / class 选择器绑定事件；需要访问 CMS 上下文时使用 window.__YU_CMS_CONTEXT__。
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

    private List<AiAgentToolResult> executeToolCalls(AiGenerationResult result, CmsPageGenerateDTO dto) {
        List<AiAgentToolCall> calls = result.toolCalls();
        if (calls == null || calls.isEmpty()) {
            calls = fallbackToolCalls(dto);
        }
        Map<String, AiAgentTool> tools = new LinkedHashMap<>();
        aiAgentToolProvider.stream().forEach(tool -> {
            String name = tool.descriptor().name();
            tools.putIfAbsent(name, tool);
            tools.putIfAbsent(safeToolName(name), tool);
        });
        return calls.stream()
                .map(call -> executeTool(tools, call))
                .toList();
    }

    private AiAgentToolResult executeTool(Map<String, AiAgentTool> tools, AiAgentToolCall call) {
        AiAgentTool tool = tools.get(call.toolName());
        if (tool == null) {
            throw new BizException("AI 工具不存在：" + call.toolName());
        }
        return tool.execute(call);
    }

    private String safeToolName(String name) {
        return name == null ? "" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private List<AiAgentToolCall> fallbackToolCalls(CmsPageGenerateDTO dto) {
        if (!StringUtils.hasText(dto.getHtmlContent())
                && !StringUtils.hasText(dto.getCssContent())
                && !StringUtils.hasText(dto.getJsContent())
                && !StringUtils.hasText(dto.getBuilderProjectJson())) {
            return List.of();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "replace-page");
        arguments.put("title", dto.getTitle());
        arguments.put("summary", dto.getSummary());
        arguments.put("htmlContent", dto.getHtmlContent());
        arguments.put("cssContent", dto.getCssContent());
        arguments.put("jsContent", dto.getJsContent());
        arguments.put("builderProjectJson", dto.getBuilderProjectJson());
        arguments.put("markdownContent", dto.getMarkdownContent());
        return List.of(new AiAgentToolCall(CmsCanvasAiTool.TOOL_NAME, arguments));
    }
}
