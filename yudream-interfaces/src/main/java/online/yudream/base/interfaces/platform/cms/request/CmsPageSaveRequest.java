package online.yudream.base.interfaces.platform.cms.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

@Data
public class CmsPageSaveRequest {
    @NotBlank(message = "页面标题不能为空")
    private String title;
    @NotBlank(message = "页面路径不能为空")
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
}
