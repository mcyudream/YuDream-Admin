package online.yudream.base.infra.platform.docs.mapper;

import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;
import online.yudream.base.infra.platform.docs.dataobj.ApiDocSettingsDO;

public class ApiDocSettingsInfraMapper {

    private ApiDocSettingsInfraMapper() {
    }

    public static ApiDocSettingsDO toDataObj(ApiDocSettings settings) {
        if (settings == null) {
            return null;
        }
        ApiDocSettingsDO dataObj = new ApiDocSettingsDO();
        dataObj.setId(settings.getId());
        dataObj.setCode(settings.getCode());
        dataObj.setEnabled(settings.isEnabled());
        dataObj.setApiKeyAccessEnabled(settings.isApiKeyAccessEnabled());
        dataObj.setTitle(settings.getTitle());
        dataObj.setDescription(settings.getDescription());
        dataObj.setDocVersion(settings.getDocVersion());
        dataObj.setOpenApiPath(settings.getOpenApiPath());
        dataObj.setSwaggerUiPath(settings.getSwaggerUiPath());
        dataObj.setVersion(settings.getVersion());
        dataObj.setCreateTime(settings.getCreateTime());
        dataObj.setUpdateTime(settings.getUpdateTime());
        return dataObj;
    }

    public static ApiDocSettings toDomain(ApiDocSettingsDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return ApiDocSettings.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .enabled(dataObj.isEnabled())
                .apiKeyAccessEnabled(dataObj.isApiKeyAccessEnabled())
                .title(dataObj.getTitle())
                .description(dataObj.getDescription())
                .docVersion(dataObj.getDocVersion())
                .openApiPath(dataObj.getOpenApiPath())
                .swaggerUiPath(dataObj.getSwaggerUiPath())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
