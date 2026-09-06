package online.yudream.base.interfaces.platform.milky.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MilkyConnectionRes {
    private String id;
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
