package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class CmsPageDTO {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String markdownContent;
    private String seoTitle;
    private String seoDescription;
    private PageStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
