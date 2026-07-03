package online.yudream.base.application.platform.integration.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class IntegrationPageQuery extends PageQuery {
    private String keyword;
}
