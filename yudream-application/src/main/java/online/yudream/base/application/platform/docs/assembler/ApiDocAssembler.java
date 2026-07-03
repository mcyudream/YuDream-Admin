package online.yudream.base.application.platform.docs.assembler;

import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;

public class ApiDocAssembler {

    private ApiDocAssembler() {
    }

    public static ApiDocSettingsDTO toDTO(ApiDocSettings settings) {
        if (settings == null) {
            return null;
        }
        return ApiDocSettingsDTO.builder()
                .id(settings.getId())
                .enabled(settings.isEnabled())
                .apiKeyAccessEnabled(settings.isApiKeyAccessEnabled())
                .title(settings.getTitle())
                .description(settings.getDescription())
                .version(settings.getDocVersion())
                .openApiPath(settings.getOpenApiPath())
                .swaggerUiPath(settings.getSwaggerUiPath())
                .updateTime(settings.getUpdateTime())
                .build();
    }
}
