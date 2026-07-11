package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SatoriChatMessageDTO {
    private String id;
    private String channelId;
    private String content;
    private String userId;
    private String userName;
    private String userAvatar;
    private Long createdAt;
}
