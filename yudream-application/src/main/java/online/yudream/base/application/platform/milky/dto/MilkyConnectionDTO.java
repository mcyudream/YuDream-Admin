package online.yudream.base.application.platform.milky.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MilkyConnectionDTO {
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String appId;
    private boolean sandbox;
    private Integer intents;
    private boolean enabled;
    private boolean credentialConfigured;
    private String commandMenuImageMode;
    private String commandMenuPublicBaseUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
