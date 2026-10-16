package online.yudream.base.application.platform.wiki.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

/**
 * 空间成员分页查询。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WikiSpaceMemberPageQuery extends PageQuery {
    private Long spaceId;
}
