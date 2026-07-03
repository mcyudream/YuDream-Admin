package online.yudream.base.application.system.menu.cmd;

import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class MenuUpdateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
}
