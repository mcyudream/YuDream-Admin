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
        dataObj.setBackupJarPath(module.getBackupJarPath());
        dataObj.setBackupName(module.getBackupName());
        dataObj.setBackupPluginVersion(module.getBackupPluginVersion());
        dataObj.setBackupDescription(module.getBackupDescription());
        dataObj.setBackupMainClass(module.getBackupMainClass());
        dataObj.setBackupSha256(module.getBackupSha256());
        dataObj.setBackupDependencies(module.getBackupDependencies());
        dataObj.setBackupSoftDependencies(module.getBackupSoftDependencies());
        dataObj.setBackupStatus(module.getBackupStatus());
        dataObj.setBackupErrorMessage(module.getBackupErrorMessage());
        dataObj.setBackupLoadedAt(module.getBackupLoadedAt());
        dataObj.setBackupEnabledAt(module.getBackupEnabledAt());
        dataObj.setBackupMenusInitialized(module.getBackupMenusInitialized());
        dataObj.setBackupRestoreIntentActive(module.getBackupRestoreIntentActive());
        dataObj.setRestoreIntentActive(module.getRestoreIntentActive());
        dataObj.setDependencies(module.getDependencies());
        dataObj.setSoftDependencies(module.getSoftDependencies());
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
                .backupJarPath(dataObj.getBackupJarPath())
                .backupName(dataObj.getBackupName())
                .backupPluginVersion(dataObj.getBackupPluginVersion())
                .backupDescription(dataObj.getBackupDescription())
                .backupMainClass(dataObj.getBackupMainClass())
                .backupSha256(dataObj.getBackupSha256())
                .backupDependencies(dataObj.getBackupDependencies())
                .backupSoftDependencies(dataObj.getBackupSoftDependencies())
                .backupStatus(dataObj.getBackupStatus())
                .backupErrorMessage(dataObj.getBackupErrorMessage())
                .backupLoadedAt(dataObj.getBackupLoadedAt())
                .backupEnabledAt(dataObj.getBackupEnabledAt())
                .backupMenusInitialized(dataObj.getBackupMenusInitialized())
                .backupRestoreIntentActive(dataObj.getBackupRestoreIntentActive())
                .restoreIntentActive(dataObj.getRestoreIntentActive())
                .dependencies(dataObj.getDependencies())
                .softDependencies(dataObj.getSoftDependencies())
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
