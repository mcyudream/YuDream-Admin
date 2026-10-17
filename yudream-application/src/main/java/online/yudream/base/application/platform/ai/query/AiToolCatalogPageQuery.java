package online.yudream.base.application.platform.ai.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;

/**
 * 工具目录分页查询：宿主系统工具、插件工具、MCP 工具与 Python 自定义工具统一一张表，
 * 前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AiToolCatalogPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "名称 / 编码 / 描述",
        showWhenFolded = true,
        order = 1,
        width = "260px"
    )
    private String keyword;

    @Searchable(
        label = "来源",
        controlType = ControlType.SELECT,
        optionsSource = "toolSourceOptions",
        order = 2
    )
    private String source;

    @Searchable(
        label = "模块",
        controlType = ControlType.SELECT,
        optionsSource = "toolModuleOptions",
        order = 3
    )
    private String moduleCode;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "toolStatusOptions",
        order = 4
    )
    private String status;
}
