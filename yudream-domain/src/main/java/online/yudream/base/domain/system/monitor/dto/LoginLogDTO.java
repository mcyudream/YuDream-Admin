package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogDTO {

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
