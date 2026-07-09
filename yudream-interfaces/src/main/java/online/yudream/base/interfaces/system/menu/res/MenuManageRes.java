package online.yudream.base.interfaces.system.menu.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MenuManageRes {

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
    private Boolean runtimeAvailable;

    @Builder.Default
    private List<MenuManageRes> children = new ArrayList<>();
}
