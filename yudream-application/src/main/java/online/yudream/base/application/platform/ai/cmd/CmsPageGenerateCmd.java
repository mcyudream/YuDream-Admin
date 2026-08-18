package online.yudream.base.application.platform.ai.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;

import java.util.List;

@Data
public class CmsPageGenerateCmd {
    private String agentCode;
    private String title;
    private String prompt;
    private String pageType;
    private String template;
    private String style;
    private String siteName;
    private String providerCode;
    private String modelCode;
    private String model;
    private String imageDataUrl;
    private String currentHtml;
    private String currentCss;
    private String currentJs;
    private String currentProjectJson;
    private String currentSelectionJson;
    private String cmsVariableContextJson;
    private boolean thinkingEnabled;
    /** agent = coding-agent 式客户端工具闭环（v2）；缺省为旧版一次性 patch 协议 */
    private String mode;
    private List<String> permissionCodes = List.of();
    private boolean permissionContextExplicit;
    private List<AiChatMessage> history = List.of();
}
