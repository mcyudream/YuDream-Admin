package online.yudream.base.infra.system.setting.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.infra.system.setting.dataobj.SettingDO;

/**
 * Setting 领域对象与数据对象互转。
 */
@NoArgsConstructor
public class SettingInfraMapper {

    public static SettingDO toDataObj(Setting setting) {
        if (setting == null) {
            return null;
        }
        SettingDO settingDO = new SettingDO();
        settingDO.setId(setting.getId());
        settingDO.setKey(setting.getKey());
        settingDO.setValue(setting.getValue());
        settingDO.setType(setting.getType());
        settingDO.setCategory(setting.getCategory());
        settingDO.setDescription(setting.getDescription());
        settingDO.setVersion(setting.getVersion());
        settingDO.setCreateTime(setting.getCreateTime());
        settingDO.setUpdateTime(setting.getUpdateTime());
        return settingDO;
    }

    public static Setting toDomain(SettingDO settingDO) {
        if (settingDO == null) {
            return null;
        }
        return Setting.builder()
                .id(settingDO.getId())
                .key(settingDO.getKey())
                .value(settingDO.getValue())
                .type(settingDO.getType())
                .category(settingDO.getCategory())
                .description(settingDO.getDescription())
                .version(settingDO.getVersion())
                .createTime(settingDO.getCreateTime())
                .updateTime(settingDO.getUpdateTime())
                .build();
    }
}
