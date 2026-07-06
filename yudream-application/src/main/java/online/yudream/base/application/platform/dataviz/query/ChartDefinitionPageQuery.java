package online.yudream.base.application.platform.dataviz.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChartDefinitionPageQuery extends PageQuery {

    private String keyword;
}
