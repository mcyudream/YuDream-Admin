package online.yudream.base.domain.platform.ai.valobj;

/**
 * 一条历史对话消息，用于向模型注入多轮上下文。
 *
 * @param role    角色：user 或 assistant
 * @param content 文本内容（不含画布 HTML/CSS 等大字段，仅对话正文）
 */
public record AiChatMessage(
        String role,
        String content
) {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equalsIgnoreCase(role);
    }
}
