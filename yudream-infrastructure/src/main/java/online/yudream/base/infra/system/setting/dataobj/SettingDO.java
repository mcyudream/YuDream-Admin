package online.yudream.base.infra.system.setting.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 系统设置数据对象。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysSetting")
public class SettingDO extends BaseDO {

    @Indexed(unique = true)
    private String key;

    private String value;

    private SettingType type;

    private String category;

    private String description;
}
