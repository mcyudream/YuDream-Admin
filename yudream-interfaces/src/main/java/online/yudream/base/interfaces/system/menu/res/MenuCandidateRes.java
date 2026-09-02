package online.yudream.base.interfaces.system.menu.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;

@Data
@Builder
public class MenuCandidateRes {

    private String code;
    private String name;
    private MenuNodeType type;
    private String parentCode;
    private String module;
    private String icon;
    private Integer sort;
    private MenuSource source;
    private String pluginCode;
    private String pluginModuleName;
}
