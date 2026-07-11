package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SatoriConversationDTO {
    private String channelId;
    private String guildId;
    private String targetUserId;
    private String name;
    private String type;
    private String avatar;
}
