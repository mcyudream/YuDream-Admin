package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;

import java.util.List;

@Data
public class AgentRunCmd {
    private Long applicationId;
    private String input;
    private String providerCode;
    private String modelCode;
    private String imageDataUrl;
    private List<String> imageDataUrls = List.of();
    private List<AgentAttachmentCmd> attachments = List.of();
    private String runtimeSystemPrompt;
    private boolean runtimeToolCallingEnabled;
    /**
     * 调用方（插件经 SPI）为本次运行显式许可的工具名单；与工作流节点自身声明的 toolCodes 取并集。
     * 仅在 runtimeToolCallingEnabled=true 时生效；宿主原生工具按权限快照校验，插件工具由网关注入。
     */
    private List<String> runtimeToolCodes = List.of();
    private List<String> permissionCodes = List.of();
    private boolean permissionContextExplicit;
    private List<AiChatMessage> history = List.of();
}
