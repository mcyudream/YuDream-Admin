package online.yudream.base.interfaces.platform.ai.res;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CmsPageGenerateRes {
    private String title;
    private String summary;
    private String htmlContent;
    private String cssContent;
    private String builderProjectJson;
    private String markdownContent;
    @Builder.Default
    private List<AiToolCallRes> tools = new ArrayList<>();
}
