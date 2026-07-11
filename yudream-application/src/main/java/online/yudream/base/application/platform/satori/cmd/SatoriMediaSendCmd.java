package online.yudream.base.application.platform.satori.cmd;

import lombok.Builder;

import java.util.List;

@Builder
public record SatoriMediaSendCmd(Long connectionId, String platform, String userId, String channelId, String content,
                                 List<Attachment> attachments) {
    public SatoriMediaSendCmd {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    @Builder
    public record Attachment(String filename, String contentType, byte[] content) {
        public Attachment {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override public byte[] content() { return content.clone(); }
    }
}
