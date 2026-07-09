package online.yudream.base.domain.system.menu.aggregate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单领域对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Menu {

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

    @Builder.Default
    private Boolean visible = true;

    private String permission;

    @Builder.Default
    private MenuStatus status = MenuStatus.ACTIVE;

    @Builder.Default
    private MenuSource source = MenuSource.SYSTEM;

    private String pluginCode;

    private String pluginModuleName;

    private String pluginRegistrationKey;

    private Boolean runtimeAvailable;

    @Builder.Default
    private List<Menu> children = new ArrayList<>();

    public String getPermissionCode() {
        return permission == null || permission.isBlank() ? code : permission;
    }

    public void updateBasic(String name,
                            MenuNodeType type,
                            String parentCode,
                            String module,
                            String icon,
                            String path,
                            String component,
                            String link,
                            Integer sort,
                            Boolean visible,
                            String permission) {
        this.name = name;
        this.type = type;
        this.parentCode = parentCode;
        this.module = module;
        this.icon = icon;
        this.path = path;
        this.component = component;
        this.link = link;
        this.sort = sort;
        this.visible = visible;
        this.permission = permission;
    }

    public boolean isVisibleInMenu() {
        return visible == null || visible;
    }

    public boolean isPluginMenu() {
        return source == MenuSource.PLUGIN;
    }

    public boolean isAvailableForRuntime() {
        return !isPluginMenu() || Boolean.TRUE.equals(runtimeAvailable);
    }

    public void activate() {
        this.status = MenuStatus.ACTIVE;
    }

    public void disable() {
        this.status = MenuStatus.DISABLED;
    }
}
