package online.yudream.base.infra.platform.agent.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformAgentApplication")
public class AgentApplicationDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private String description;
    private String icon;
    private String systemPrompt;
    private String workflowJson;
    private List<String> toolCodes;
    private AgentApplicationStatus status;
}
