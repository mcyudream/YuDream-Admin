package online.yudream.base.application.platform.document.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.Searchable;

/**
 * Word 模板与生成记录分页查询。
 * <p>
 * 模板列表与生成记录共用本查询类；过滤语义沿用仓储既有实现：模板列表命中模板名称 / 编码 / 描述，
 * 生成记录命中模板编码 / 生成文件名 / 异常信息。前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WordDocumentPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "模板名称 / 编码 / 文件名",
        showWhenFolded = true,
        order = 1,
        width = "280px"
    )
    private String keyword;
}
