package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CmsChromeAiTool implements AiAgentTool {

    public static final String TOOL_NAME = "cms.chrome.style";
    public static final String PERMISSION_CODE = "platform:ai:tool:cms-chrome-style";
    private static final Set<String> TARGETS = Set.of("header", "footer", "both");

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "CMS Header/Footer 校验与样式",
                "在首页整体画布中校验固定 Header/Footer 结构并修改其 CSS，不拆分页面编辑目标",
                PERMISSION_CODE,
                "AI 校验和修改 CMS Header/Footer 样式",
                "平台能力",
                "只允许校验固定结构或生成作用于首页整体画布的 CSS 修改",
                Map.ofEntries(
                        Map.entry("action", "validate | set-styles | append-css"),
                        Map.entry("target", "header | footer | both"),
                        Map.entry("htmlContent", "当前首页整体 HTML，用于结构校验"),
                        Map.entry("cssContent", "需要追加到首页整体 CSS 的样式"),
                        Map.entry("selector", "Header/Footer CSS 选择器"),
                        Map.entry("styles", "set-styles 使用的 CSS 属性对象"),
                        Map.entry("style", "可选 CSS 字符串")
                )
        );
    }

    @Override
    public AiAgentToolResult execute(AiAgentToolCall call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        String action = text(args.getOrDefault("action", "validate"));
        String target = text(args.getOrDefault("target", "both"));
        if (!TARGETS.contains(target)) {
            throw new BizException("Header/Footer 校验目标不支持：" + target);
        }
        if ("validate".equals(action)) {
            return validationResult(target, args);
        }
        if (!Set.of("set-styles", "append-css").contains(action)) {
            throw new BizException("Header/Footer 工具只允许校验或修改样式：" + action);
        }
        String selector = text(args.get("selector"));
        if ("set-styles".equals(action) && "both".equals(target) && selector.isBlank()) {
            throw new BizException("同时修改 Header/Footer 时，set-styles 必须提供 CSS 选择器；或使用 append-css");
        }
        if (selector.isBlank() && !"both".equals(target)) {
            selector = "[data-yb-chrome=\"" + target + "\"]";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target", "home");
        putIfPresent(payload, "selector", selector);
        putIfPresent(payload, "styles", args.get("styles"));
        putIfPresent(payload, "style", args.get("style"));
        putIfPresent(payload, "cssContent", args.get("cssContent"));
        return new AiAgentToolResult(
                TOOL_NAME,
                action,
                PERMISSION_CODE,
                text(args.getOrDefault("message", "Header/Footer 样式修改已转为首页整体画布指令")),
                payload
        );
    }

    private AiAgentToolResult validationResult(String target, Map<String, Object> args) {
        List<String> issues = new ArrayList<>();
        String html = text(args.get("htmlContent"));
        String css = text(args.get("cssContent"));
        if (html.isBlank()) {
            issues.add("请提供首页整体 HTML，不能只校验独立 Header/Footer 片段");
        }
        else {
            if (("header".equals(target) || "both".equals(target))
                    && count(html, "data-yb-chrome=\"header\"") != 1) {
                issues.add("首页整体画布必须包含且只包含一个固定 Header");
            }
            if (("footer".equals(target) || "both".equals(target))
                    && count(html, "data-yb-chrome=\"footer\"") != 1) {
                issues.add("首页整体画布必须包含且只包含一个固定 Footer");
            }
        }
        if (html.contains("<script") || html.toLowerCase().contains("javascript:")) {
            issues.add("Header/Footer 结构中不允许脚本或 javascript 协议");
        }
        if (css.toLowerCase().contains("javascript:")) {
            issues.add("CSS 中不允许 javascript 协议");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target", target);
        payload.put("valid", issues.isEmpty());
        payload.put("issues", issues);
        return new AiAgentToolResult(TOOL_NAME, "validate", PERMISSION_CODE,
                issues.isEmpty() ? "Header/Footer 固定结构校验通过" : "Header/Footer 校验发现问题", payload);
    }

    private int count(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
