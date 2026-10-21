package online.yudream.base.application.system.menu.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MenuManageDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 内部稳定主键，只读展示/导出用；不是 API 主键，业务标识仍是 code。 */
    private Long id;

    private String code;
    private String name;
    private MenuNodeType type;
    private String parentCode;
    private String displayParentCode;
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
    private List<MenuManageDTO> children = new ArrayList<>();
}
