package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentToolRes {
    private String id;
    private String name;
    private String code;
    private String description;
    private AgentToolType type;
    private String inputSchemaJson;
    private String pythonCode;
    private Integer timeoutMillis;
    private String permissionCode;
    private Boolean enabled;
    private LocalDateTime updateTime;
}
