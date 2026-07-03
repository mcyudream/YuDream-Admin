package online.yudream.base.application.platform.docs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDocSettingsDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
