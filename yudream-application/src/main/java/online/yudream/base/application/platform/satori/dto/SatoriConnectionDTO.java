package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SatoriConnectionDTO {
    private Long id;
    private String name;
    private String baseUrl;
    private String platform;
    private String userId;
    private boolean enabled;
    private boolean credentialConfigured;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
