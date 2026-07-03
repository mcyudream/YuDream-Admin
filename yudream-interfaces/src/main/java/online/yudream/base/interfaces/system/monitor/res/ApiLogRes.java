package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiLogRes {
    private Long id;
    private String method;
    private String path;
    private String query;
    private String requestBody;
    private Integer status;
    private Long costMs;
    private Boolean success;
    private Long loginId;
    private String username;
    private String nickname;
    private String ip;
    private String userAgent;
    private String errorMessage;
    private LocalDateTime createTime;
}
