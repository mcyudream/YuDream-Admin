package online.yudream.base.application.platform.chat.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.service.AgentSkillAppService;
import online.yudream.base.application.platform.ai.service.AiToolModuleAppService;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.dto.ChatToolOptionsDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 对话输入框的「工具 / 技能」选择项：系统工具模块（网页、知识库…）与 MCP 服务按调用者权限与开关列出；
 * 技能为技能库中已启用的技能。选择只决定本条消息装配哪些工具与技能，不授予权限。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ChatToolOptionAppService {

    private final CapabilityAppService capabilities;
    private final AiToolModuleAppService toolModules;
    private final AgentSkillAppService skills;

    public ChatToolOptionsDTO options(Collection<String> permissionCodes) {
        capabilities.ensureEnabled("ai", "AI 助手");
        List<ChatToolOptionsDTO.Skill> enabledSkills = skills.allEnabled().stream()
                .map(skill -> new ChatToolOptionsDTO.Skill(skill.getName(), skill.getDescription()))
                .toList();
        return new ChatToolOptionsDTO(toolModules.chatToolOptions(permissionCodes), enabledSkills);
    }
}
