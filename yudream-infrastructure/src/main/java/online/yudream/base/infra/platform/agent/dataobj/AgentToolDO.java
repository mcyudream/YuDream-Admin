package online.yudream.base.infra.platform.agent.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformAgentTool")
public class AgentToolDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private String description;
    private AgentToolType type;
    private String inputSchemaJson;
    private String outputExampleJson;
    private String pythonCode;
    private Integer timeoutMillis;
    private String permissionCode;
    private Boolean enabled;
}
