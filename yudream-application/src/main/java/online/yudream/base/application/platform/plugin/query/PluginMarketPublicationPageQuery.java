package online.yudream.base.application.platform.plugin.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class PluginMarketPublicationPageQuery extends PageQuery {

    private String status;
    private Boolean mine;
}
