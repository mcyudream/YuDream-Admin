package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;

@Data
public class AgentToolSaveCmd {
    private Long id;
    private String name;
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
