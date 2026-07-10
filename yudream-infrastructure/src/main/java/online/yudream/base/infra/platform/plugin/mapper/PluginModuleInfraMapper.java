package online.yudream.base.infra.platform.plugin.mapper;

import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.infra.platform.plugin.dataobj.PluginModuleDO;

public class PluginModuleInfraMapper {

    private PluginModuleInfraMapper() {
    }

    public static PluginModuleDO toDataObj(PluginModule module) {
        if (module == null) {
            return null;
        }
        PluginModuleDO dataObj = new PluginModuleDO();
        dataObj.setId(module.getId());
        dataObj.setCode(module.getCode());
        dataObj.setName(module.getName());
        dataObj.setPluginVersion(module.getPluginVersion());
        dataObj.setDescription(module.getDescription());
        dataObj.setMainClass(module.getMainClass());
        dataObj.setJarPath(module.getJarPath());
        dataObj.setDependencies(module.getDependencies());
        dataObj.setStatus(module.getStatus());
        dataObj.setErrorMessage(module.getErrorMessage());
        dataObj.setLoadedAt(module.getLoadedAt());
        dataObj.setEnabledAt(module.getEnabledAt());
        dataObj.setMenusInitialized(module.getMenusInitialized());
        dataObj.setVersion(module.getVersion());
        dataObj.setCreateTime(module.getCreateTime());
        dataObj.setUpdateTime(module.getUpdateTime());
        return dataObj;
    }

    public static PluginModule toDomain(PluginModuleDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return PluginModule.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .pluginVersion(dataObj.getPluginVersion())
                .description(dataObj.getDescription())
                .mainClass(dataObj.getMainClass())
                .jarPath(dataObj.getJarPath())
                .dependencies(dataObj.getDependencies())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .loadedAt(dataObj.getLoadedAt())
                .enabledAt(dataObj.getEnabledAt())
                .menusInitialized(dataObj.getMenusInitialized())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
