package online.yudream.base.application.platform.cms.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class CmsPageQuery extends PageQuery {
    private String keyword;
    private String category;
    private String tag;
}
