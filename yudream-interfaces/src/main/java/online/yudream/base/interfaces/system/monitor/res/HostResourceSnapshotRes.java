package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HostResourceSnapshotRes {
    private String hostname;
    private String osName;
    private String osVersion;
    private String architecture;
    private String cpuName;
    private Integer cpuPhysicalCount;
    private Integer cpuLogicalCount;
    private Double cpuUsagePercent;
    private Double load1;
    private Double load5;
    private Double load15;
    private Long memoryTotalBytes;
    private Long memoryUsedBytes;
    private Long memoryAvailableBytes;
    private Double memoryUsagePercent;
    private Long swapTotalBytes;
    private Long swapUsedBytes;
    private List<HostDiskRes> disks;
    private Long diskTotalBytes;
    private Long diskUsedBytes;
    private Double diskUsagePercent;
    private List<HostNetworkRes> networks;
    private Long networkRecvBytesPerSec;
    private Long networkSentBytesPerSec;
    private Long jvmPid;
    private String javaVersion;
    private Long jvmUptimeMs;
    private Long jvmHeapUsedBytes;
    private Long jvmHeapMaxBytes;
    private Long jvmNonHeapUsedBytes;
    private Long jvmNonHeapMaxBytes;
    private Long jvmDirectUsedBytes;
    private Long jvmDirectMaxBytes;
    private Integer jvmThreadCount;
    private Integer jvmDaemonThreadCount;
    private Long jvmLoadedClassCount;
    private List<JvmMemoryPoolRes> memoryPools;
    private List<JvmGcRes> garbageCollectors;
    private List<HostProcessRes> topProcesses;
    private List<HostThreadHotspotRes> topThreads;
    private String notice;
    private LocalDateTime sampledAt;
}
