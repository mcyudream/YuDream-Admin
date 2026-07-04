package online.yudream.base.interfaces.platform.ai.request;

import lombok.Data;

@Data
public class CmsPageGenerateRequest {
    private String title;
    private String prompt;
    private String pageType;
    private String style;
    private String siteName;
    private String model;
    private String imageDataUrl;
}
