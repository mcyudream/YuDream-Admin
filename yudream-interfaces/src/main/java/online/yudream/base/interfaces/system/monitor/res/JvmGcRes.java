package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JvmGcRes {
    private String name;
    private Long collectionCount;
    private Long collectionTimeMs;
}
