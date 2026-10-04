package online.yudream.base.domain.platform.agent.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台技能库条目（框架级统一管理）：AgentScope AgentSkillRepository 的持久化侧，
 * 所有 Harness Agent 每轮推理经 &lt;available_skills&gt; 发现并按需加载。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkill extends BaseDomain {

    private String name;
    private String description;
    /** SKILL.md 正文（frontmatter 由框架按 name/description 合成） */
    private String content;
    /** references/scripts 等资源：相对路径 → 文本内容 */
    private Map<String, String> resources;
    private Boolean enabled;
    private Boolean builtin;

    public static AgentSkill create(String name, String description, String content, Map<String, String> resources) {
        AgentSkill skill = new AgentSkill();
        skill.apply(name, description, content, resources, true, false);
        return skill;
    }

    public void apply(String name, String description, String content, Map<String, String> resources,
                      Boolean enabled, Boolean builtin) {
        this.name = required(name, "技能名称不能为空");
        this.description = required(description, "技能描述不能为空");
        this.content = required(content, "技能内容不能为空");
        this.resources = resources == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(resources));
        this.enabled = enabled == null || enabled;
        this.builtin = builtin != null && builtin;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
