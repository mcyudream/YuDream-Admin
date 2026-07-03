package online.yudream.base.domain.platform.docs.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDocSettings extends BaseDomain {

    public static final String DEFAULT_CODE = "default";

    private String code;
    private boolean enabled;
    private boolean apiKeyAccessEnabled;
    private String title;
    private String description;
    private String docVersion;
    private String openApiPath;
    private String swaggerUiPath;

    public static ApiDocSettings createDefault() {
        return ApiDocSettings.builder()
                .code(DEFAULT_CODE)
                .enabled(false)
                .apiKeyAccessEnabled(false)
                .title("YuDream Admin API")
                .description("YuDream Admin 系统接口文档")
                .docVersion("1.0.0")
                .openApiPath("/v3/api-docs")
                .swaggerUiPath("/swagger-ui/index.html")
                .build();
    }

    public void update(boolean enabled,
                       boolean apiKeyAccessEnabled,
                       String title,
                       String description,
                       String version,
                       String openApiPath,
                       String swaggerUiPath) {
        if (title == null || title.isBlank()) {
            throw new BizException("API 文档标题不能为空");
        }
        this.enabled = enabled;
        this.apiKeyAccessEnabled = apiKeyAccessEnabled;
        this.title = title.trim();
        this.description = description == null ? "" : description.trim();
        this.docVersion = version == null || version.isBlank() ? "1.0.0" : version.trim();
        this.openApiPath = normalizePath(openApiPath, "/v3/api-docs");
        this.swaggerUiPath = normalizePath(swaggerUiPath, "/swagger-ui/index.html");
    }

    private String normalizePath(String path, String fallback) {
        if (path == null || path.isBlank()) {
            return fallback;
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
