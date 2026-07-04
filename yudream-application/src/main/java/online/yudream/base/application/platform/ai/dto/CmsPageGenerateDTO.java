package online.yudream.base.application.platform.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CmsPageGenerateDTO {
    private String title;
    private String summary;
    private String htmlContent;
    private String cssContent;
    private String builderProjectJson;
    private String markdownContent;
}
