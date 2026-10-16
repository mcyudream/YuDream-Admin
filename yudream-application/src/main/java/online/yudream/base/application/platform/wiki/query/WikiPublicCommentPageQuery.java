package online.yudream.base.application.platform.wiki.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

/**
 * 公开端评论分页查询（只返回已发布评论）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WikiPublicCommentPageQuery extends PageQuery {
}
