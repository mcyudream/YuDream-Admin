package online.yudream.base.application.platform.ai.cmd;

import lombok.Data;

@Data
public class CmsPageGenerateCmd {
    private String title;
    private String prompt;
    private String pageType;
    private String style;
    private String siteName;
    private String model;
    private String imageDataUrl;
}
