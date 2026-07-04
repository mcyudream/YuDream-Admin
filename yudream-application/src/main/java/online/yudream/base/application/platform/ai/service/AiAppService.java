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
import java.util.stream.Collectors;

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
        AiGenerationRequest request = new AiGenerationRequest(systemPrompt(cmd), userPrompt(cmd), cmd.getImageDataUrl(), cmd.getModel(), config);
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
                3. 分析完成后，必须调用 cms.canvas.patch 修改画布；不要把工具参数、HTML、CSS 或 JSON 直接输出给用户。
                4. 工具执行后，只用简短中文说明完成了什么。

                工具说明：
                - web.fetch（模型工具名 web_fetch）：抓取公开网页的标题、描述和正文摘要，用于设计分析，不修改画布。
                - cms.canvas.patch（模型工具名 cms_canvas_patch）：修改 GrapesJS CMS 画布，action 支持 replace-page、set-html、set-css、load-project、add-html、remove-selector。

                cms.canvas.patch 参数要求：
                - htmlContent 只返回页面主体内容，不要 html/head/body/script，不要系统导航栏或 footer。
                - cssContent 只写作用于 htmlContent 的 scoped 风格，类名使用 yb-ai- 前缀。
                - builderProjectJson 可以为空字符串；如果无法生成 GrapesJS project JSON，请让 htmlContent/cssContent 足够完整。
                - title、summary、markdownContent 可以作为辅助字段传给工具。

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

                当前 GrapesJS Project JSON：
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
                defaultText(cmd.getCurrentProjectJson(), "无"),
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
        Map<String, AiAgentTool> tools = aiAgentToolProvider.stream()
                .collect(Collectors.toMap(tool -> tool.descriptor().name(), tool -> tool, (left, right) -> left, LinkedHashMap::new));
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

    private List<AiAgentToolCall> fallbackToolCalls(CmsPageGenerateDTO dto) {
        if (!StringUtils.hasText(dto.getHtmlContent())
                && !StringUtils.hasText(dto.getCssContent())
                && !StringUtils.hasText(dto.getBuilderProjectJson())) {
            return List.of();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "replace-page");
        arguments.put("title", dto.getTitle());
        arguments.put("summary", dto.getSummary());
        arguments.put("htmlContent", dto.getHtmlContent());
        arguments.put("cssContent", dto.getCssContent());
        arguments.put("builderProjectJson", dto.getBuilderProjectJson());
        arguments.put("markdownContent", dto.getMarkdownContent());
        return List.of(new AiAgentToolCall(CmsCanvasAiTool.TOOL_NAME, arguments));
    }
}
