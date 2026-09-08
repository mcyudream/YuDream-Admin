package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResourceMetricPointRes {
    private LocalDateTime sampledAt;
    private Double cpuUsagePercent;
    private Long memoryUsedBytes;
    private Long memoryTotalBytes;
    private Double memoryUsagePercent;
    private Long swapUsedBytes;
    private Long swapTotalBytes;
    private Long diskUsedBytes;
    private Long diskTotalBytes;
    private Double diskUsagePercent;
    private Long networkRecvBytesPerSec;
    private Long networkSentBytesPerSec;
    private Long jvmHeapUsedBytes;
    private Long jvmHeapMaxBytes;
    private Long jvmNonHeapUsedBytes;
}
