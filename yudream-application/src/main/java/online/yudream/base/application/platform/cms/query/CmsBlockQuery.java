package online.yudream.base.application.platform.cms.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;

@EqualsAndHashCode(callSuper = true)
@Data
public class CmsBlockQuery extends PageQuery {
    private String keyword;
    private String category;
    private CmsBlockKind kind;
}
