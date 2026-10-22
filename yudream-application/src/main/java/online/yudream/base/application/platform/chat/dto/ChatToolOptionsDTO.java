package online.yudream.base.application.platform.chat.dto;

import online.yudream.base.application.platform.ai.dto.ChatToolOptionDTO;

import java.util.List;

/** 对话中可勾选的工具模块与技能（按当前用户权限与开关过滤）。 */
public record ChatToolOptionsDTO(List<ChatToolOptionDTO> modules, List<Skill> skills) {
    public record Skill(String name, String description) {
    }
}
