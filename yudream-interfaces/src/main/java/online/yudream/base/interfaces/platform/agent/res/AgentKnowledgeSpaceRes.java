package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentKnowledgeSpaceRes {
    private String slug;
    private String name;
    private String embeddingProviderCode;
    private String embeddingModelCode;
    private Integer topK;
    private Boolean graphEnabled;
    private Boolean rerankEnabled;
}
