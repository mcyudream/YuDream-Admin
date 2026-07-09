package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendModuleRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String entry;
    private String moduleName;
    private String sdkVersion;
    private String integrity;
    private String menuTitle;
    private String menuIcon;
    private Integer menuSort;
    private String parentCode;
    private Boolean visible;
    private MenuStatus status;
    private String menuCode;
    private MenuNodeType menuType;
    private String menuModule;
    private String menuPath;
    private String menuComponent;
    private String menuLink;
    private String menuPermission;

    @Builder.Default
    private List<PluginFrontendRouteRes> routes = new ArrayList<>();
}
