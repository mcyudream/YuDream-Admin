package online.yudream.base.application.platform.ai.cmd;

import lombok.Data;

@Data
public class CmsPageGenerateCmd {
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
