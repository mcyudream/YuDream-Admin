package online.yudream.base.interfaces.platform.cms.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CmsPageRes {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String excerpt;
    private String coverImageUrl;
    private List<String> categories;
    private List<String> tags;
    private String markdownContent;
    private String htmlContent;
    private String cssContent;
    private String builderProjectJson;
    private String seoTitle;
    private String seoDescription;
    private PageTemplate template;
    private PageStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
