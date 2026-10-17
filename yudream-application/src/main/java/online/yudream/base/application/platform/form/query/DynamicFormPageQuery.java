package online.yudream.base.application.platform.form.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;

/**
 * 动态表单分页查询。
 * <p>
 * 过滤语义沿用仓储既有实现：keyword 命中名称 / 编码 / 描述，status 命中表单状态；
 * 前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DynamicFormPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "表单名称 / 编码 / 描述",
        showWhenFolded = true,
        order = 1,
        width = "260px"
    )
    private String keyword;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "formStatusOptions",
        order = 2
    )
    private DynamicFormStatus status;
}
