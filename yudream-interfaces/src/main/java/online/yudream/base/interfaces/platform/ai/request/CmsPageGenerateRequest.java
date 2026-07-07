package online.yudream.base.interfaces.platform.ai.request;

import lombok.Data;

import java.util.List;

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
    private String currentSelectionJson;
    private String cmsVariableContextJson;
    private boolean thinkingEnabled;
    private List<ChatMessage> history;

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
