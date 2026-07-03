package online.yudream.base.infra.platform.cms.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformCmsPage")
public class CmsPageDO extends BaseDO {
    private String title;
    @Indexed(unique = true)
    private String slug;
    private String summary;
    private String markdownContent;
    private String seoTitle;
    private String seoDescription;
    private PageStatus status;
    private LocalDateTime publishedAt;
}
