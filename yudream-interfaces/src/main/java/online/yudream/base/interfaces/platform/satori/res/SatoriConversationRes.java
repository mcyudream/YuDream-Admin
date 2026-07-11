package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SatoriConversationRes {
    private String channelId;
    private String guildId;
    private String targetUserId;
    private String name;
    private String type;
    private String avatar;
}
