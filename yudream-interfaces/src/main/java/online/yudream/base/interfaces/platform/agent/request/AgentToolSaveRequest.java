package online.yudream.base.interfaces.platform.agent.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentToolSaveRequest {
    @NotBlank private String name;
    @NotBlank private String code;
    private String description;
    private String inputSchemaJson;
    @NotBlank private String outputExampleJson;
    @NotBlank private String pythonCode;
    private Integer timeoutMillis;
    private String permissionCode;
    private Boolean enabled;
}
