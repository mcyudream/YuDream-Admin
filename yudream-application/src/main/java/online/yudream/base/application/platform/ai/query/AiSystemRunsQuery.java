package online.yudream.base.application.platform.ai.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;

/**
 * 系统 AI 运行查询：过滤语义沿用运行存储既有实现（activeOnly 决定是否只枚举运行中任务），
 * 列表由存储按枚举上限返回（全部任务最多 100 条 + 运行中任务），未做服务端切片，
 * 分页在页面内完成；前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AiSystemRunsQuery extends PageQuery {

    @Searchable(
        label = "运行范围",
        controlType = ControlType.SELECT,
        optionsSource = "aiSystemRunActiveOptions",
        showWhenFolded = true,
        order = 1,
        width = "160px"
    )
    private Boolean activeOnly;
}
