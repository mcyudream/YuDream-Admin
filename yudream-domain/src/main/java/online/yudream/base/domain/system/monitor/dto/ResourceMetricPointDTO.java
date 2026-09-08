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
public class ResourceMetricPointDTO {

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
