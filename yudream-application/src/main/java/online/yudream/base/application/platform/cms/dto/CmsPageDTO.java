package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

import java.time.LocalDateTime;

@Data
@Builder
public class CmsPageDTO {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String excerpt;
    private String coverImageUrl;
    private String markdownContent;
    private String htmlContent;
    private String seoTitle;
    private String seoDescription;
    private PageTemplate template;
    private PageStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
