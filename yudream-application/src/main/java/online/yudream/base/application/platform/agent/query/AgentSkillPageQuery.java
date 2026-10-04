package online.yudream.base.application.platform.agent.query;

import lombok.Data;

@Data
public class AgentSkillPageQuery {

    private int page = 1;

    private int size = 10;

    private String keyword;
}
