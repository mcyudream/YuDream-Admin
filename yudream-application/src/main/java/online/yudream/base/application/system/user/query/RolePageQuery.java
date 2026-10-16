package online.yudream.base.application.system.user.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class RolePageQuery extends PageQuery {
    @Searchable(
        label = "关键词",
        placeholder = "角色名称 / 编码",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;

    @Searchable(
        label = "所属部门",
        controlType = ControlType.SELECT,
        optionsSource = "deptOptions",
        order = 2
    )
    private Long deptId;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "roleStatusOptions",
        order = 3
    )
    private RoleStatus status;
}
