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
public class ApiLogDTO {

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
