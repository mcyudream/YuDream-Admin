package online.yudream.base.infra.platform.ai.runtime;

/**
 * RuntimeContext 中的按次调用系统提示词覆盖：调度方（如 GENERAL 聊天）可针对
 * 本次运行注入专用提示词；空值时保持 agent 构建期的 sysPrompt。
 */
public record AgentSystemPromptOverride(String prompt) {

    public boolean present() {
        return prompt != null && !prompt.isBlank();
    }
}
