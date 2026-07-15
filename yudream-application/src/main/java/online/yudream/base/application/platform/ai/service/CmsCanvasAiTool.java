package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class CmsCanvasAiTool implements AiAgentTool {

    public static final String TOOL_NAME = "cms.canvas.patch";
    public static final String PERMISSION_CODE = "platform:ai:tool:cms-canvas-patch";

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "CMS 画布修改",
                "向 GrapesJS CMS 构建器返回结构化画布增删改指令",
                PERMISSION_CODE,
                "AI 修改 CMS 画布",
                "平台能力",
                "允许 AI Agent 修改 CMS 构建器画布",
                Map.ofEntries(
                        Map.entry("action", "replace-page | set-html | set-css | append-css | set-js | append-js | load-project | add-html | remove-selector | replace-selected | set-selected-html | append-to-selected | prepend-to-selected | set-selected-text | set-attributes | set-styles | add-class | remove-class | remove-selected"),
                        Map.entry("target", "page | home"),
                        Map.entry("htmlContent", "页面主体 HTML；add-html 时为要追加到画布末尾的单个区块"),
                        Map.entry("cssContent", "页面 CSS；add-html 时必须在同一次调用中覆盖 htmlContent 引入的全部 class；append-css 仅修改已有结构"),
                        Map.entry("jsContent", "页面 JavaScript；append-js 时为要追加的脚本片段，不要包含 script 标签"),
                        Map.entry("builderProjectJson", "GrapesJS Project JSON"),
                        Map.entry("selector", "可选 CSS 选择器；为空时 selected 系列动作作用于当前选中元素"),
                        Map.entry("textContent", "set-selected-text 使用的文本内容"),
                        Map.entry("attributes", "set-attributes 使用的属性对象"),
                        Map.entry("styles", "set-styles 使用的样式对象"),
                        Map.entry("className", "add-class/remove-class 使用的类名")
                )
        );
    }

    @Override
    public AiAgentToolResult execute(AiAgentToolCall call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        String action = text(args.get("action"));
        if (!StringUtils.hasText(action)) {
            action = "replace-page";
        }
        if (!isSupported(action)) {
            throw new BizException("AI 工具动作不支持：" + action);
        }
        requireCssForStructuralHtml(action, args);
        String target = text(args.get("target"));
        if ("header".equals(target) || "footer".equals(target)) {
            throw new BizException("Header/Footer 必须作为首页整体画布处理，请使用 cms.chrome.style 工具校验或修改样式");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "target", target);
        putIfPresent(payload, "title", args.get("title"));
        putIfPresent(payload, "summary", args.get("summary"));
        putIfPresent(payload, "htmlContent", args.get("htmlContent"));
        putIfPresent(payload, "cssContent", args.get("cssContent"));
        putIfPresent(payload, "jsContent", args.get("jsContent"));
        putIfPresent(payload, "builderProjectJson", args.get("builderProjectJson"));
        putIfPresent(payload, "markdownContent", args.get("markdownContent"));
        putIfPresent(payload, "selector", args.get("selector"));
        putIfPresent(payload, "textContent", args.get("textContent"));
        putIfPresent(payload, "attributes", args.get("attributes"));
        putIfPresent(payload, "styles", args.get("styles"));
        putIfPresent(payload, "style", args.get("style"));
        putIfPresent(payload, "className", args.get("className"));
        return new AiAgentToolResult(
                TOOL_NAME,
                action,
                PERMISSION_CODE,
                text(args.getOrDefault("message", "画布修改指令已生成")),
                payload
        );
    }

    private boolean isSupported(String action) {
        return Set.of(
                "replace-page", "set-html", "set-css", "append-css", "set-js", "append-js",
                "load-project", "add-html", "remove-selector", "replace-selected", "set-selected-html",
                "append-to-selected", "prepend-to-selected", "set-selected-text", "set-attributes",
                "set-styles", "add-class", "remove-class", "remove-selected"
        ).contains(action);
    }

    private void requireCssForStructuralHtml(String action, Map<String, Object> args) {
        if (!Set.of("replace-page", "set-html", "add-html").contains(action)) {
            return;
        }
        CmsCanvasStyleCoverage.requireComplete(
                text(args.get("htmlContent")),
                text(args.get("cssContent")),
                action
        );
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
