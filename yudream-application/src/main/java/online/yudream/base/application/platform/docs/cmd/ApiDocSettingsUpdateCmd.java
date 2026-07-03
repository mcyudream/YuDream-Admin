package online.yudream.base.application.platform.docs.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ApiDocSettingsUpdateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean enabled;
    private boolean apiKeyAccessEnabled;
    private String title;
    private String description;
    private String version;
    private String openApiPath;
    private String swaggerUiPath;
}
