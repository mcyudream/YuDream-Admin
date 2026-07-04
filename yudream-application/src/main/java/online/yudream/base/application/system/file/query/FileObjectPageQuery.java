package online.yudream.base.application.system.file.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
public class FileObjectPageQuery extends PageQuery {
    private String keyword;
    private String module;
    private Boolean publicAccess;
}
