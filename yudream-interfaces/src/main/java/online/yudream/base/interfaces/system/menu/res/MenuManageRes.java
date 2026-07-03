package online.yudream.base.interfaces.system.menu.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
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
    private String permission;
    private MenuStatus status;

    @Builder.Default
    private List<MenuManageRes> children = new ArrayList<>();
}
