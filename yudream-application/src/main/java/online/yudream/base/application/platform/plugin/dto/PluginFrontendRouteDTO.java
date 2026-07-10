package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendRouteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String path;
    private String name;
    private String title;
    private String icon;
    private String parentPath;
    private String parentTitle;
    private String parentIcon;
    private Integer parentSort;
    private String component;
    private String permission;
    private Integer sort;
    private Boolean hideInMenu;
    private String parentCode;
    private Boolean visible;
    private MenuStatus status;
    private String menuCode;
    private MenuNodeType type;
    private String module;
    private String link;
    private String parentMenuCode;
    private String parentParentCode;
    private MenuNodeType parentType;
    private String parentModule;
    private String parentComponent;
    private String parentLink;
    private String parentPermission;
    private Boolean parentVisible;
    private MenuStatus parentStatus;
}
