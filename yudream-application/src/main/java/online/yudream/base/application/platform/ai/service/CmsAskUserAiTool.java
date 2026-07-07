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

/**
 * 需求不明确时，AI 通过该工具向用户提问并给出可点击的选项。
 * 工具产出一段 TokUI DSL，前端用 TokUI 渲染为可点击的建议卡片；用户点击后作为下一轮消息发送。
 */
@Component
public class CmsAskUserAiTool implements AiAgentTool {

    public static final String TOOL_NAME = "cms.ask.user";
    public static final String PERMISSION_CODE = "platform:ai:tool:cms-ask-user";

    private static final int MAX_OPTIONS = 4;

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "向用户提问",
                "当需求不明确时，向用户提出一个澄清问题并给出 2-4 个可点击选项，等待用户选择后再继续。",
                PERMISSION_CODE,
                "AI 向用户提问",
                "平台能力",
                "允许 AI Agent 在需求不明确时向用户提问并给出选项。",
                Map.of(
                        "question", Map.of("type", "string", "description", "向用户提出的澄清问题"),
                        "options", Map.of("type", "array", "description", "2-4 个可选项，每项含 title（简短标题）和可选 desc（说明）")
                )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiAgentToolResult execute(AiAgentToolCall call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        String question = text(args.get("question"));
        if (!StringUtils.hasText(question)) {
            throw new BizException("cms.ask.user 缺少 question 参数");
        }
        List<Option> options = parseOptions(args.get("options"));
        if (options.isEmpty()) {
            throw new BizException("cms.ask.user 至少需要一个选项");
        }
        String dsl = buildTokuiDsl(question, options);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("options", options.stream().map(Option::toMap).toList());
        payload.put("tokui", dsl);
        return new AiAgentToolResult(
                TOOL_NAME,
                "ask",
                PERMISSION_CODE,
                question,
                payload
        );
    }

    @SuppressWarnings("unchecked")
    private List<Option> parseOptions(Object raw) {
        List<Option> options = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (options.size() >= MAX_OPTIONS) {
                    break;
                }
                if (item instanceof Map<?, ?> map) {
                    String title = text(map.get("title"));
                    if (!StringUtils.hasText(title)) {
                        title = text(map.get("label"));
                    }
                    if (StringUtils.hasText(title)) {
                        options.add(new Option(title, text(map.get("desc"))));
                    }
                } else if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    options.add(new Option(String.valueOf(item).trim(), ""));
                }
            }
        }
        return options;
    }

    private String buildTokuiDsl(String question, List<Option> options) {
        StringBuilder sb = new StringBuilder();
        sb.append("[callout t:info]").append(escape(question)).append("[/callout]");
        sb.append("[suggestions cols:2]");
        for (Option option : options) {
            sb.append("[suggestion tt:").append(quote(option.title()));
            if (StringUtils.hasText(option.desc())) {
                sb.append(" tx:").append(quote(option.desc()));
            }
            sb.append(" clk:pick]");
        }
        sb.append("[/suggestions]");
        return sb.toString();
    }

    /**
     * TokUI 属性值若含空格或方括号需用双引号包裹；同时把内部双引号与方括号做安全处理，避免破坏 DSL 解析。
     */
    private String quote(String value) {
        return "\"" + value.replace("\"", "'").replace("[", "（").replace("]", "）") + "\"";
    }

    private String escape(String value) {
        return value.replace("[", "（").replace("]", "）");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Option(String title, String desc) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("title", title);
            map.put("desc", desc);
            return map;
        }
    }
}
