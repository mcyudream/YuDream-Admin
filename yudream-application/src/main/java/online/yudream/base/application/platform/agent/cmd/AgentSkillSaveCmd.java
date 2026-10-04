package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;

import java.util.Map;

@Data
public class AgentSkillSaveCmd {

    private Long id;

    private String name;

    private String description;

    private String content;

    private Map<String, String> resources;

    private Boolean enabled;
}
