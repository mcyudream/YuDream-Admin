package online.yudream.base.interfaces.system.menu.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;

import java.io.Serial;
import java.io.Serializable;

@Data
public class MenuCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "菜单编码不能为空")
    private String code;
    @NotBlank(message = "菜单名称不能为空")
    private String name;
    @NotNull(message = "菜单类型不能为空")
    private MenuNodeType type;
    private String parentCode;
    private String module;
    private String icon;
    private String path;
    private String component;
    private String link;
    private Integer sort;
    private String permission;
}
