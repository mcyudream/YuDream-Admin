package online.yudream.base.application.platform.milky.sandbox.dto;

/**
 * 沙盒策略连接可选项；connectionId 为雪花 ID，按全局规则以字符串传递
 */
public record QqSandboxConnectionOptionDTO(String connectionId, String name) { }
