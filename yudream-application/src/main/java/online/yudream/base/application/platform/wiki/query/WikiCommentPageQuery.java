package online.yudream.base.application.platform.wiki.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;

/**
 * 治理端评论分页查询；status 为空表示全部状态。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WikiCommentPageQuery extends PageQuery {
    private Long spaceId;
    private WikiCommentStatus status;
}
