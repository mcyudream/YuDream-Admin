package online.yudream.base.application.system.menu.query;

import lombok.Data;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class MenuTreeQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Searchable(
        label = "关键词",
        placeholder = "名称 / 编码 / 权限标识 / 路径",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;

    @Searchable(
        label = "菜单类型",
        controlType = ControlType.SELECT,
        optionsSource = "menuTypeOptions",
        order = 2
    )
    private MenuNodeType type;

    @Searchable(
        label = "菜单状态",
        controlType = ControlType.SELECT,
        optionsSource = "menuStatusOptions",
        order = 3
    )
    private MenuStatus status;
}
