package online.yudream.base.application.system.menu.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;

@EqualsAndHashCode(callSuper = true)
@Data
public class MenuCandidatePageQuery extends PageQuery {

    private String keyword;

    private String currentCode;

    private MenuSource source;
}
