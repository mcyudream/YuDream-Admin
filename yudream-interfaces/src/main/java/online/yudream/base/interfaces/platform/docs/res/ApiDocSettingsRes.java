package online.yudream.base.interfaces.platform.docs.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiDocSettingsRes {
    private Long id;
    private boolean enabled;
    private boolean apiKeyAccessEnabled;
    private String title;
    private String description;
    private String version;
    private String openApiPath;
    private String swaggerUiPath;
    private LocalDateTime updateTime;
}
