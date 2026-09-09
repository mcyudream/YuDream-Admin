package online.yudream.base.application.platform.mail.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class InboundMailPageQuery extends PageQuery {
    private String keyword;
}
