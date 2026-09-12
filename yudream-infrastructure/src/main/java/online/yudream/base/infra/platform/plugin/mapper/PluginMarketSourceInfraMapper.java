package online.yudream.base.infra.platform.plugin.mapper;

import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.infra.platform.plugin.dataobj.PluginMarketSourceDO;

public class PluginMarketSourceInfraMapper {

    private PluginMarketSourceInfraMapper() {
    }

    public static PluginMarketSourceDO toDataObj(PluginMarketSource source) {
        if (source == null) {
            return null;
        }
        PluginMarketSourceDO dataObj = new PluginMarketSourceDO();
        dataObj.setId(source.getId());
        dataObj.setCode(source.getCode());
        dataObj.setName(source.getName());
        dataObj.setType(source.getType());
        dataObj.setRootUrl(source.getRootUrl());
        dataObj.setToken(source.getToken());
        dataObj.setEnabled(source.getEnabled());
        dataObj.setBuiltIn(source.getBuiltIn());
        dataObj.setSortOrder(source.getSortOrder());
        dataObj.setSyncStatus(source.getSyncStatus());
        dataObj.setSyncErrorMessage(source.getSyncErrorMessage());
        dataObj.setSyncedAt(source.getSyncedAt());
        dataObj.setVersion(source.getVersion());
        dataObj.setCreateTime(source.getCreateTime());
        dataObj.setUpdateTime(source.getUpdateTime());
        return dataObj;
    }

    public static PluginMarketSource toDomain(PluginMarketSourceDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return PluginMarketSource.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .type(dataObj.getType())
                .rootUrl(dataObj.getRootUrl())
                .token(dataObj.getToken())
                .enabled(dataObj.getEnabled())
                .builtIn(dataObj.getBuiltIn())
                .sortOrder(dataObj.getSortOrder())
                .syncStatus(dataObj.getSyncStatus())
                .syncErrorMessage(dataObj.getSyncErrorMessage())
                .syncedAt(dataObj.getSyncedAt())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
