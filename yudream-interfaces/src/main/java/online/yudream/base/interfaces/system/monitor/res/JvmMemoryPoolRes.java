package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JvmMemoryPoolRes {
    private String name;
    private String type;
    private Long usedBytes;
    private Long committedBytes;
    private Long maxBytes;
    private Double usagePercent;
}
