package online.yudream.base.application.platform.ai.dto;

import java.util.List;

/** 对话中可选择的工具模块（系统工具模块或 MCP 服务）：只含已开放且调用者至少能用其中一个工具的模块。 */
public record ChatToolOptionDTO(String code, String name, String description, String icon, String source, List<String> tools) {
}
