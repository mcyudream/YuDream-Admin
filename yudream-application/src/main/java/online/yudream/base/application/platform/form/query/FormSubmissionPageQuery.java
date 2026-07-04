package online.yudream.base.application.platform.form.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class FormSubmissionPageQuery extends PageQuery {
    private Long formId;
}
