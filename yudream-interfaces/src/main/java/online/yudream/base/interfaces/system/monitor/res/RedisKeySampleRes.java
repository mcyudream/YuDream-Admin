package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedisKeySampleRes {
    private String key;
    private String type;
    private Long ttl;
}
