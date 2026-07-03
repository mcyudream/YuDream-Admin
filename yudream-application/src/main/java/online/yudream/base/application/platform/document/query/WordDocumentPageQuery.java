package online.yudream.base.application.platform.document.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class WordDocumentPageQuery extends PageQuery {
    private String keyword;
}
