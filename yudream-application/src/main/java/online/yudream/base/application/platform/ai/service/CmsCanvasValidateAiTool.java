package online.yudream.base.application.platform.ai.service;

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
public class CmsCanvasValidateAiTool implements AiAgentTool {

    public static final String TOOL_NAME = "cms.canvas.validate";

    private static final List<String> INTERACTIVE_MARKERS = List.of(
            "data-yb-action", "data-yb-tabs", "data-yb-carousel", "data-yb-accordion",
            "data-yb-modal", "data-yb-toggle"
    );

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "校验 CMS 画布完整性",
                "在完成构建前校验最终 HTML、CSS 和 JavaScript 是否完整，并返回必须修复的问题",
                CmsCanvasAiTool.PERMISSION_CODE,
                "AI 校验 CMS 画布",
                "平台能力",
                "允许 AI Agent 对 CMS 画布代码执行只读完整性校验",
                Map.of(
                        "htmlContent", "最终页面主体的完整 HTML，不是单个区块",
                        "cssContent", "最终页面使用的完整 CSS",
                        "jsContent", "最终页面使用的完整 JavaScript；没有交互时可为空"
                )
        );
    }

    @Override
    public AiAgentToolResult execute(AiAgentToolCall call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        String html = text(args.get("htmlContent"));
        String css = text(args.get("cssContent"));
        String js = text(args.get("jsContent"));
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!StringUtils.hasText(html)) {
            errors.add("最终 HTML 为空");
        }
        if (StringUtils.hasText(html) && !StringUtils.hasText(css)) {
            errors.add("最终 CSS 为空，页面只有结构没有样式");
        }
        if (StringUtils.hasText(css) && !balanced(css, '{', '}')) {
            errors.add("CSS 花括号不完整");
        }
        if (StringUtils.hasText(js) && (!balanced(js, '{', '}') || !balanced(js, '(', ')') || !balanced(js, '[', ']'))) {
            errors.add("JavaScript 括号不完整");
        }
        if (html.toLowerCase().contains("<script")) {
            errors.add("HTML 中不能包含 script 标签，脚本必须放入 jsContent");
        }
        if (js.toLowerCase().contains("<script")) {
            errors.add("jsContent 不能包含 script 标签");
        }
        validateJavaScriptLifecycle(js, errors);

        Set<String> classes = CmsCanvasStyleCoverage.htmlClasses(html);
        List<String> uncoveredClasses = CmsCanvasStyleCoverage.uncoveredEditableClasses(html, css).stream()
                .limit(20)
                .toList();
        if (!uncoveredClasses.isEmpty()) {
            errors.add("以下 HTML 类没有对应 CSS 选择器：" + String.join(", ", uncoveredClasses));
        }

        boolean interactive = INTERACTIVE_MARKERS.stream().anyMatch(html::contains);
        if (interactive && !StringUtils.hasText(js)) {
            errors.add("页面包含交互标记，但 JavaScript 为空");
        }
        if (!interactive && StringUtils.hasText(js)) {
            warnings.add("页面包含 JavaScript，但未发现标准 data-yb 交互标记，请确认选择器能够命中元素");
        }

        boolean valid = errors.isEmpty();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", valid);
        payload.put("errors", errors);
        payload.put("warnings", warnings);
        payload.put("stats", Map.of(
                "htmlLength", html.length(),
                "cssLength", css.length(),
                "jsLength", js.length(),
                "classCount", classes.size()
        ));
        return new AiAgentToolResult(
                TOOL_NAME,
                "validate",
                CmsCanvasAiTool.PERMISSION_CODE,
                valid ? "画布 HTML、CSS、JavaScript 完整性校验通过" : "画布完整性校验未通过，请修复后重新校验",
                payload
        );
    }

    private void validateJavaScriptLifecycle(String js, List<String> errors) {
        if (!StringUtils.hasText(js)) {
            return;
        }
        String normalized = js.toLowerCase();
        boolean registersCleanup = normalized.contains("window.__yu_cms_register_cleanup__");
        if (normalized.contains("requestanimationframe(")
                && (!registersCleanup || !normalized.contains("cancelanimationframe("))) {
            errors.add("使用 requestAnimationFrame 时必须通过 window.__YU_CMS_REGISTER_CLEANUP__ 注册清理，并调用 cancelAnimationFrame");
        }
        if (normalized.contains("setinterval(")
                && (!registersCleanup || !normalized.contains("clearinterval("))) {
            errors.add("使用 setInterval 时必须通过 window.__YU_CMS_REGISTER_CLEANUP__ 注册清理，并调用 clearInterval");
        }
        if (normalized.contains("addeventlistener(")
                && (!registersCleanup || !normalized.contains("removeeventlistener("))) {
            errors.add("使用 addEventListener 时必须通过 window.__YU_CMS_REGISTER_CLEANUP__ 注册清理，并调用 removeEventListener");
        }
        if (normalized.contains("setanimationloop(")
                && (!registersCleanup || !normalized.contains("setanimationloop(null"))) {
            errors.add("使用 Three.js setAnimationLoop 时必须通过 window.__YU_CMS_REGISTER_CLEANUP__ 注册清理，并调用 setAnimationLoop(null)");
        }
    }

    private boolean balanced(String value, char opening, char closing) {
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == opening) {
                depth++;
            }
            else if (current == closing && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
