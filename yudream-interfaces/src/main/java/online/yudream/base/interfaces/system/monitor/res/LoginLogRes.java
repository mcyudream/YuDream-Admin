package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoginLogRes {
    private Long id;
    private String username;
    private Long userId;
    private Boolean success;
    private String message;
    private String ip;
    private String userAgent;
    private String token;
    private LocalDateTime createTime;
}
