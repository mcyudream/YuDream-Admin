package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SatoriChatMessageRes {
    private String id;
    private String channelId;
    private String content;
    private String userId;
    private String userName;
    private String userAvatar;
    private Long createdAt;
}
