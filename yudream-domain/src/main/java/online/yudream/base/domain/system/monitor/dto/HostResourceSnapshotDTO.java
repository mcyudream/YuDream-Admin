package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostResourceSnapshotDTO {

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

    private List<HostDiskDTO> disks;

    private Long diskTotalBytes;

    private Long diskUsedBytes;

    private Double diskUsagePercent;

    private List<HostNetworkDTO> networks;

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

    private List<JvmMemoryPoolDTO> memoryPools;

    private List<JvmGcDTO> garbageCollectors;

    private List<HostProcessDTO> topProcesses;

    private List<HostThreadHotspotDTO> topThreads;

    private String notice;

    private LocalDateTime sampledAt;
}
