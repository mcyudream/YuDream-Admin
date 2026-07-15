package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentCatalogRes {
    private List<AgentKnowledgeSpaceRes> knowledgeSpaces;
    private List<AgentModelRes> models;
}
