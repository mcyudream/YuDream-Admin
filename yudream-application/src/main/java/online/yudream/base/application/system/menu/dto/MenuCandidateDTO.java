package online.yudream.base.application.system.menu.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class MenuCandidateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
