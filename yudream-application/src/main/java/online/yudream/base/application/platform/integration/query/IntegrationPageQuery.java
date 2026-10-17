package online.yudream.base.application.platform.integration.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.Searchable;

/**
 * 集成调用分页查询。
 * <p>
 * 连接器 / HTTP 日志 / 运行脚本 / 运行日志四个列表共用本查询类；过滤语义沿用仓储既有实现：
 * 连接器命中名称 / 编码 / 请求地址，HTTP 日志命中连接器编码 / 请求地址，运行脚本命中名称 / 编码，
 * 运行日志命中脚本编码。前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class IntegrationPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "名称 / 编码 / 地址 / 日志关键字",
        showWhenFolded = true,
        order = 1,
        width = "300px"
    )
    private String keyword;
}
