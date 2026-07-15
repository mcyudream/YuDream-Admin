package online.yudream.base.application.platform.agent.dto;

import java.util.List;

public record AgentCatalogDTO(
        List<AgentKnowledgeSpaceDTO> knowledgeSpaces,
        List<AgentModelDTO> models
) {
}
