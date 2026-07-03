package online.yudream.base.interfaces.platform.docs.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApiDocSettingsUpdateRequest {

    private boolean enabled;
    private boolean apiKeyAccessEnabled;

    @NotBlank(message = "API 文档标题不能为空")
    private String title;

    private String description;
    private String version;
    private String openApiPath;
    private String swaggerUiPath;
}
