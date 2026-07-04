package online.yudream.base.interfaces.platform.ai.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CmsPageGenerateRequest {
    private String title;

    @NotBlank(message = "生成需求不能为空")
    private String prompt;

    private String pageType;
    private String style;
    private String siteName;
}
