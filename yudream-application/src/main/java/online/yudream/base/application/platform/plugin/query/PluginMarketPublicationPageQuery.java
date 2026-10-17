package online.yudream.base.application.platform.plugin.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;

/**
 * 市场源发布物分页查询。
 * <p>
 * keyword 命中插件编码 / 显示名 / 版本号，status 命中发布状态，
 * mine 只取当前操作者自己的发布物（无查看全部权限时强制为真）；
 * 前端发布与审核页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PluginMarketPublicationPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "插件名称 / 编码 / 版本",
        showWhenFolded = true,
        order = 1,
        width = "260px"
    )
    private String keyword;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "publicationStatusOptions",
        order = 2
    )
    private String status;

    private Boolean mine;
}
