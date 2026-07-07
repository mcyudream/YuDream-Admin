package online.yudream.base.application.platform.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CmsPageGenerateDTO {
    private String title;
    private String summary;
    private String htmlContent;
    private String cssContent;
    private String jsContent;
    private String builderProjectJson;
    private String markdownContent;
    @Builder.Default
    private List<AiToolCallDTO> tools = new ArrayList<>();
}
