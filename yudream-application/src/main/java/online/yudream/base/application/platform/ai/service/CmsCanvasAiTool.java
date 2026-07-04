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
                        "action", "replace-page | set-html | set-css | load-project | add-html | remove-selector",
                        "htmlContent", "页面主体 HTML",
                        "cssContent", "页面 CSS",
                        "builderProjectJson", "GrapesJS Project JSON",
                        "selector", "remove-selector 使用的 CSS 选择器"
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
                || "load-project".equals(action)
                || "add-html".equals(action)
                || "remove-selector".equals(action);
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
