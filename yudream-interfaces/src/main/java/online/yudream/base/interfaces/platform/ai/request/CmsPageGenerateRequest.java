package online.yudream.base.interfaces.platform.ai.request;

import lombok.Data;

@Data
public class CmsPageGenerateRequest {
    private String title;
    private String prompt;
    private String pageType;
    private String style;
    private String siteName;
    private String providerCode;
    private String modelCode;
    private String model;
    private String imageDataUrl;
    private String currentHtml;
    private String currentCss;
    private String currentProjectJson;
    private boolean thinkingEnabled;
}
