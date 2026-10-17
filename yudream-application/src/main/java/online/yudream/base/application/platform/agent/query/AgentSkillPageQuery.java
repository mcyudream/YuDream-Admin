package online.yudream.base.application.platform.agent.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.Searchable;

/** 平台技能库分页查询：搜索字段由 @Searchable 声明，前端按 queryClass 生成筛选表单。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSkillPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "名称 / 描述",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;
}
