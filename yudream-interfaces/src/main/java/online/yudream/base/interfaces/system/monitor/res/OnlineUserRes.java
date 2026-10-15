package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OnlineUserRes {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    /** 会话用户头像 URL，可空（取不到时为 null）。 */
    private String avatar;
    private String email;
    private Long timeout;
    private Long activeTimeout;
    private String device;
}
