package online.yudream.base.interfaces.platform.docs.assembler;

import online.yudream.base.application.platform.docs.cmd.ApiDocSettingsUpdateCmd;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.interfaces.platform.docs.request.ApiDocSettingsUpdateRequest;
import online.yudream.base.interfaces.platform.docs.res.ApiDocSettingsRes;

public class ApiDocWebAssembler {

    private ApiDocWebAssembler() {
    }

    public static ApiDocSettingsUpdateCmd toCmd(ApiDocSettingsUpdateRequest request) {
        ApiDocSettingsUpdateCmd cmd = new ApiDocSettingsUpdateCmd();
        cmd.setEnabled(request.isEnabled());
        cmd.setApiKeyAccessEnabled(request.isApiKeyAccessEnabled());
        cmd.setTitle(request.getTitle());
        cmd.setDescription(request.getDescription());
        cmd.setVersion(request.getVersion());
        cmd.setOpenApiPath(request.getOpenApiPath());
        cmd.setSwaggerUiPath(request.getSwaggerUiPath());
        return cmd;
    }

    public static ApiDocSettingsRes toRes(ApiDocSettingsDTO dto) {
        return ApiDocSettingsRes.builder()
                .id(dto.getId())
                .enabled(dto.isEnabled())
                .apiKeyAccessEnabled(dto.isApiKeyAccessEnabled())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .version(dto.getVersion())
                .openApiPath(dto.getOpenApiPath())
                .swaggerUiPath(dto.getSwaggerUiPath())
                .updateTime(dto.getUpdateTime())
                .build();
    }
}
