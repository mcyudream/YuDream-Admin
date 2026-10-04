package online.yudream.base.infra.platform.agent.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 技能资源以 JSON 字符串存储：资源路径（references/style.md 等）天然含点号，
 * Mongo map 键不允许。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformAgentSkill")
public class AgentSkillDO extends BaseDO {

    @Indexed(unique = true)
    private String name;

    private String description;

    private String content;

    /** resources 的 JSON 串（相对路径 → 文本内容） */
    private String resourcesJson;

    private boolean enabled;

    private boolean builtin;
}
