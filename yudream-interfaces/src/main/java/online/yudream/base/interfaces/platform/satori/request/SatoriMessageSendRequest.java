package online.yudream.base.interfaces.platform.satori.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.satori.message.SatoriMessageContent;

import java.util.List;
import java.util.Map;

@Data
public class SatoriMessageSendRequest {
    @NotBlank
    private String platform;
    @NotBlank
    private String userId;
    @NotBlank
    private String channelId;
    private SatoriMessageContent.Type type = SatoriMessageContent.Type.TEXT;
    private String content;
    private List<Attachment> attachments;
    private Map<String, Object> referrer;

    @Data
    public static class Attachment {
        @NotBlank
        private String url;
        private String title;
        private String contentType;
    }
}
