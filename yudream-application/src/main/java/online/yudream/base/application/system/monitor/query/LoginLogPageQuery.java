package online.yudream.base.application.system.monitor.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;

/**
 * 登录日志分页查询。
 * <p>
 * 过滤语义沿用仓储既有实现：keyword 命中用户名 / IP / 结果，success 命中登录结果；
 * 前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LoginLogPageQuery extends PageQuery {
    @Searchable(
        label = "关键词",
        placeholder = "用户名 / IP / 结果",
        showWhenFolded = true,
        order = 1,
        width = "260px"
    )
    private String keyword;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "logSuccessOptions",
        order = 2
    )
    private Boolean success;
}
