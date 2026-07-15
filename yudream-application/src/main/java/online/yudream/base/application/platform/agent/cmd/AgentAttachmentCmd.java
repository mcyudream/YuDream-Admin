package online.yudream.base.application.platform.agent.cmd;

public record AgentAttachmentCmd(
        String name,
        String contentType,
        Long size,
        String dataUrl
) {
}
