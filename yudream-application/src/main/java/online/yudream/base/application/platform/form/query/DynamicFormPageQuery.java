package online.yudream.base.application.platform.form.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class DynamicFormPageQuery extends PageQuery {
    private String keyword;
    private DynamicFormStatus status;
}
