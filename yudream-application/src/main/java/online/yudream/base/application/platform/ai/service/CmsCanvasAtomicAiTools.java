package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class CmsCanvasAtomicAiTools {

    @Bean
    public AiAgentTool cmsCanvasSelectedTextTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.text",
                "修改选中元素文案",
                "快速修改当前选中的 GrapesJS 元素文本，不替换整页。",
                "set-selected-text",
                "选中元素文案已更新",
                List.of("selector", "textContent", "htmlContent"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "textContent", "新的纯文本内容",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasSelectedHtmlTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.html",
                "替换选中元素内容",
                "快速替换当前选中 GrapesJS 元素的内部 HTML，不替换整页。",
                "set-selected-html",
                "选中元素内容已更新",
                List.of("selector", "htmlContent", "cssContent"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "htmlContent", "新的内部 HTML 片段",
                        "cssContent", "可选配套 CSS 片段",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasSelectedStyleTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.style",
                "修改选中元素样式",
                "快速修改当前选中 GrapesJS 元素的内联样式，不替换结构。",
                "set-styles",
                "选中元素样式已更新",
                List.of("selector", "styles", "style"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "styles", Map.of("type", "object", "description", "CSS 属性对象，例如 {\"color\":\"#111827\"}"),
                        "style", "可选 CSS 字符串，例如 color:#111827;font-weight:700",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasAddBlockTool() {
        return new FixedCanvasTool(
                "cms.canvas.block.add",
                "追加画布区块",
                "向 GrapesJS 画布末尾追加一个独立 HTML 区块，可附带本区块 CSS/JS。",
                "add-html",
                "新区块已追加到画布",
                List.of("title", "summary", "htmlContent", "cssContent", "jsContent", "markdownContent"),
                Map.of(
                        "title", "可选区块标题",
                        "summary", "可选区块摘要",
                        "htmlContent", "要追加到画布末尾的单个区块 HTML",
                        "cssContent", "该区块需要追加的 CSS",
                        "jsContent", "该区块需要追加的 JavaScript，不要包含 script 标签",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasSelectedRemoveTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.remove",
                "删除选中元素",
                "删除当前选中的 GrapesJS 元素或 selector 命中的第一个元素。",
                "remove-selected",
                "选中元素已删除",
                List.of("selector"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    private static final class FixedCanvasTool implements AiAgentTool {

        private final AiAgentToolDescriptor descriptor;
        private final String action;
        private final String defaultMessage;
        private final List<String> payloadKeys;

        private FixedCanvasTool(
                String name,
                String title,
                String description,
                String action,
                String defaultMessage,
                List<String> payloadKeys,
                Map<String, Object> inputSchema
        ) {
            this.descriptor = new AiAgentToolDescriptor(
                    name,
                    title,
                    description,
                    CmsCanvasAiTool.PERMISSION_CODE,
                    "AI 修改 CMS 画布",
                    "平台能力",
                    "允许 AI Agent 修改 CMS 构建器画布",
                    inputSchema
            );
            this.action = action;
            this.defaultMessage = defaultMessage;
            this.payloadKeys = payloadKeys;
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
            Map<String, Object> payload = new LinkedHashMap<>();
            payloadKeys.forEach(key -> putIfPresent(payload, key, args.get(key)));
            return new AiAgentToolResult(
                    CmsCanvasAiTool.TOOL_NAME,
                    action,
                    CmsCanvasAiTool.PERMISSION_CODE,
                    text(args.getOrDefault("message", defaultMessage)),
                    payload
            );
        }

        private void putIfPresent(Map<String, Object> target, String key, Object value) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                target.put(key, value);
            }
        }

        private String text(Object value) {
            String text = value == null ? "" : String.valueOf(value).trim();
            return StringUtils.hasText(text) ? text : defaultMessage;
        }
    }
}
