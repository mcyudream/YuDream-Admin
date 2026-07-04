package online.yudream.base.application.system.security.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class ApiKeyPageQuery extends PageQuery {
    private String keyword;
    private Long creatorUserId;
}
