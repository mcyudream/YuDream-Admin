package online.yudream.base.application.platform.mail.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.Searchable;

/**
 * 入站邮件分页查询。
 * <p>
 * 过滤语义沿用邮箱网关既有实现：keyword 命中主题 / 发件人 / 正文；
 * 前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InboundMailPageQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "主题 / 发件人 / 正文",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;
}
