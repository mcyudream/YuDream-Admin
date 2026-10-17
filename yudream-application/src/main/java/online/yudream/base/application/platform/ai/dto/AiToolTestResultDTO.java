package online.yudream.base.application.platform.ai.dto;

import java.util.Map;

/** 管理端工具测试结果：真实调用一次工具（系统/插件/MCP/Python），返回时长、结果与失败原因。 */
public record AiToolTestResultDTO(
        boolean success,
        String message,
        String action,
        String toolCode,
        String source,
        long durationMillis,
        Map<String, Object> payload
) {
    public AiToolTestResultDTO {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static AiToolTestResultDTO success(String toolCode, String source, String action, String message,
                                              long durationMillis, Map<String, Object> payload) {
        return new AiToolTestResultDTO(true, message, action, toolCode, source, durationMillis, payload);
    }

    public static AiToolTestResultDTO failure(String toolCode, String source, String message, long durationMillis) {
        return new AiToolTestResultDTO(false, message, "error", toolCode, source, durationMillis, Map.of());
    }
}
