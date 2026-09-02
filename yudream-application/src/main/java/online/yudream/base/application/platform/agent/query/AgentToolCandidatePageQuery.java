package online.yudream.base.application.platform.agent.query;

import lombok.Data;
import online.yudream.base.application.common.PageQuery;

@Data
public class AgentToolCandidatePageQuery extends PageQuery {
    private String keyword;
}
