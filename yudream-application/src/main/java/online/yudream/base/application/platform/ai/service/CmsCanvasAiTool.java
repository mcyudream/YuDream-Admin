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
                Map.of(
                        "action", "replace-page | set-html | set-css | append-css | load-project | add-html | remove-selector | replace-selected | set-selected-html | append-to-selected | prepend-to-selected | set-selected-text | set-attributes | set-styles | add-class | remove-class | remove-selected",
                        "htmlContent", "页面主体 HTML；add-html 时为要追加到画布末尾的单个区块",
                        "cssContent", "页面 CSS；append-css 时为要追加的样式片段",
                        "builderProjectJson", "GrapesJS Project JSON",
                        "selector", "可选 CSS 选择器；为空时 selected 系列动作作用于当前选中元素",
                        "textContent", "set-selected-text 使用的文本内容",
                        "attributes", "set-attributes 使用的属性对象",
                        "styles", "set-styles 使用的样式对象",
                        "className", "add-class/remove-class 使用的类名"
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
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "title", args.get("title"));
        putIfPresent(payload, "summary", args.get("summary"));
        putIfPresent(payload, "htmlContent", args.get("htmlContent"));
        putIfPresent(payload, "cssContent", args.get("cssContent"));
        putIfPresent(payload, "builderProjectJson", args.get("builderProjectJson"));
        putIfPresent(payload, "markdownContent", args.get("markdownContent"));
        putIfPresent(payload, "selector", args.get("selector"));
        putIfPresent(payload, "textContent", args.get("textContent"));
        putIfPresent(payload, "attributes", args.get("attributes"));
        putIfPresent(payload, "styles", args.get("styles"));
        putIfPresent(payload, "style", args.get("style"));
        putIfPresent(payload, "className", args.get("className"));
        putIfPresent(payload, "target", args.get("target"));
        return new AiAgentToolResult(
                TOOL_NAME,
                action,
                PERMISSION_CODE,
                text(args.getOrDefault("message", "画布修改指令已生成")),
                payload
        );
    }

    private boolean isSupported(String action) {
        return "replace-page".equals(action)
                || "set-html".equals(action)
                || "set-css".equals(action)
                || "append-css".equals(action)
                || "load-project".equals(action)
                || "add-html".equals(action)
                || "remove-selector".equals(action)
                || "replace-selected".equals(action)
                || "set-selected-html".equals(action)
                || "append-to-selected".equals(action)
                || "prepend-to-selected".equals(action)
                || "set-selected-text".equals(action)
                || "set-attributes".equals(action)
                || "set-styles".equals(action)
                || "add-class".equals(action)
                || "remove-class".equals(action)
                || "remove-selected".equals(action);
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
