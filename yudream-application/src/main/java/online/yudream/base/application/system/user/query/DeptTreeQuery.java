package online.yudream.base.application.system.user.query;

import lombok.Data;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DeptTreeQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Searchable(
        label = "关键词",
        placeholder = "部门名称 / 描述",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;

    @Searchable(
        label = "上级部门",
        controlType = ControlType.SELECT,
        optionsSource = "parentDeptOptions",
        order = 2
    )
    private Long parentId;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "deptStatusOptions",
        order = 3
    )
    private DeptStatus status;
}
