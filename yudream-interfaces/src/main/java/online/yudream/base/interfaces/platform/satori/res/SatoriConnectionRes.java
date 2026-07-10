package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SatoriConnectionRes {
    private String id;
    private String name;
    private String baseUrl;
    private boolean enabled;
    private boolean credentialConfigured;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
