package online.yudream.base.application.system.user.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserPageQuery extends PageQuery {
    @Searchable(
        label = "关键词",
        placeholder = "用户名/昵称/邮箱/手机号",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;

    @Searchable(
        label = "部门",
        controlType = ControlType.SELECT,
        optionsSource = "deptOptions",
        order = 2
    )
    private Long deptId;

    @Searchable(
        label = "角色",
        controlType = ControlType.SELECT,
        optionsSource = "roleOptions",
        order = 3
    )
    private Long roleId;

    @Searchable(
        label = "邮箱验证",
        controlType = ControlType.SELECT,
        optionsSource = "emailVerifiedOptions",
        order = 4
    )
    private Boolean emailVerified;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "userStatusOptions",
        order = 5
    )
    private UserStatus status;
}
