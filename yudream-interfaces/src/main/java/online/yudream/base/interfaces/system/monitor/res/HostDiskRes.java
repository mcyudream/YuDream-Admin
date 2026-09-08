package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostDiskRes {
    private String name;
    private String mount;
    private String type;
    private Long totalBytes;
    private Long usedBytes;
    private Long usableBytes;
    private Double usagePercent;
}
