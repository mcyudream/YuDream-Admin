package online.yudream.base.application.platform.milky.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.Searchable;

/** QQ 消息连接分页查询：搜索字段由 @Searchable 声明，前端按 queryClass 生成筛选表单。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MilkyConnectionPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "连接名称 / 服务地址",
        showWhenFolded = true,
        order = 1,
        width = "260px"
    )
    private String keyword;
}
