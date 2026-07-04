package online.yudream.base.infra.platform.capability.mapper;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.infra.platform.capability.dataobj.CapabilityModuleDO;

public class CapabilityModuleInfraMapper {

    public static CapabilityModuleDO toDataObj(CapabilityModule module) {
        if (module == null) {
            return null;
        }
        CapabilityModuleDO dataObj = new CapabilityModuleDO();
        dataObj.setId(module.getId());
        dataObj.setCode(module.getCode());
        dataObj.setName(module.getName());
        dataObj.setType(module.getType());
        dataObj.setDescription(module.getDescription());
        dataObj.setIcon(module.getIcon());
        dataObj.setSort(module.getSort());
        dataObj.setEnabled(module.getEnabled());
        dataObj.setConfig(module.getConfig());
        dataObj.setDependencies(module.getDependencies());
        dataObj.setVersion(module.getVersion());
        dataObj.setCreateTime(module.getCreateTime());
        dataObj.setUpdateTime(module.getUpdateTime());
        return dataObj;
    }

    public static CapabilityModule toDomain(CapabilityModuleDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return CapabilityModule.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .type(dataObj.getType())
                .description(dataObj.getDescription())
                .icon(dataObj.getIcon())
                .sort(dataObj.getSort())
                .enabled(dataObj.getEnabled())
                .config(dataObj.getConfig())
                .dependencies(dataObj.getDependencies())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
