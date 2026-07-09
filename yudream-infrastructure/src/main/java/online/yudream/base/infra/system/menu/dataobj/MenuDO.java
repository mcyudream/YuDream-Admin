package online.yudream.base.infra.system.menu.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 菜单数据对象。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysMenu")
@CompoundIndex(
        name = "uk_plugin_menu_registration",
        def = "{'source': 1, 'pluginCode': 1, 'pluginRegistrationKey': 1}",
        unique = true,
        partialFilter = "{'source': 'PLUGIN'}"
)
public class MenuDO extends BaseDO {

    @Indexed(unique = true)
    private String code;

    private String name;

    private MenuNodeType type;

    private String parentCode;

    private String module;

    private String icon;

    private String path;

    private String component;

    private String link;

    private Integer sort;

    private Boolean visible;

    private String permission;

    private MenuStatus status;

    private MenuSource source;

    private String pluginCode;

    private String pluginModuleName;

    private String pluginRegistrationKey;

    private Boolean runtimeAvailable;
}
