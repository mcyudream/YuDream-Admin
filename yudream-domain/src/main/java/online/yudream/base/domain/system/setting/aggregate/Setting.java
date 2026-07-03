package online.yudream.base.domain.system.setting.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.setting.enumerate.SettingType;

/**
 * 系统设置聚合根。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Setting extends BaseDomain {

    /**
     * 设置项 key，全局唯一。
     */
    private String key;

    /**
     * 设置项值，统一以字符串存储，根据 type 解析。
     */
    private String value;

    /**
     * 值类型。
     */
    private SettingType type;

    /**
     * 分类，便于前端分组展示。
     */
    private String category;

    /**
     * 描述。
     */
    private String description;
}
