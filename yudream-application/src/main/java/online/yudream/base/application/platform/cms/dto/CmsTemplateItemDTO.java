package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CmsTemplateItemDTO {
    private String id;
    private String source;
    private String title;
    private String slug;
    private String summary;
    private String excerpt;
    private String url;
    private String content;
    private String htmlContent;
    private String markdownContent;
    private String spaceSlug;
    private String path;
    private String updatedAt;
}
