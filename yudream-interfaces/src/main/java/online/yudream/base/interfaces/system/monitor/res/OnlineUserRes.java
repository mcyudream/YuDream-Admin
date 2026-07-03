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
    private String email;
    private Long timeout;
    private Long activeTimeout;
    private String device;
}
