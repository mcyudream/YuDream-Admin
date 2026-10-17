package online.yudream.base.application.system.monitor.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;

/**
 * 接口日志分页查询。
 * <p>
 * 过滤语义沿用仓储既有实现：keyword 命中接口地址 / 请求方式 / IP / 异常信息，success 命中调用结果；
 * 前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ApiLogPageQuery extends PageQuery {
    @Searchable(
        label = "关键词",
        placeholder = "接口地址 / 请求方式 / IP / 异常信息",
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
