package online.yudream.base.infra.system.monitor.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "sysResourceMetric")
public class ResourceMetricDO extends BaseDO {

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
