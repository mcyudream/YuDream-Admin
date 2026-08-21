package online.yudream.base.application.platform.milky.sandbox.dto;

import java.util.List;

/**
 * 策略连接的沙盒群上下文：真实群列表与机器人自身 ID；selfId 获取失败时为 null，由前端回退手输
 */
public record QqSandboxGroupsDTO(String selfId, List<QqSandboxGroupOptionDTO> groups) { }
