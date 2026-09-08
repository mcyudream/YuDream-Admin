package online.yudream.base.application.system.monitor.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;
import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import online.yudream.base.domain.system.monitor.repo.ResourceMetricRepo;
import online.yudream.base.domain.system.monitor.service.HostResourceGateway;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HostResourceAppService {

    private static final Set<Integer> ALLOWED_HOURS = Set.of(1, 6, 24);

    private final HostResourceGateway hostResourceGateway;
    private final ResourceMetricRepo resourceMetricRepo;

    public HostResourceSnapshotDTO snapshot() {
        return hostResourceGateway.snapshot();
    }

    public List<ResourceMetricPointDTO> history(int hours) {
        int windowHours = ALLOWED_HOURS.contains(hours) ? hours : 1;
        return resourceMetricRepo.findSince(LocalDateTime.now().minusHours(windowHours));
    }

    public void recordSample() {
        HostResourceSnapshotDTO snapshot = hostResourceGateway.snapshot();
        resourceMetricRepo.save(toPoint(snapshot));
    }

    static ResourceMetricPointDTO toPoint(HostResourceSnapshotDTO snapshot) {
        return ResourceMetricPointDTO.builder()
                .sampledAt(snapshot.getSampledAt() == null ? LocalDateTime.now() : snapshot.getSampledAt())
                .cpuUsagePercent(snapshot.getCpuUsagePercent())
                .memoryUsedBytes(snapshot.getMemoryUsedBytes())
                .memoryTotalBytes(snapshot.getMemoryTotalBytes())
                .memoryUsagePercent(snapshot.getMemoryUsagePercent())
                .swapUsedBytes(snapshot.getSwapUsedBytes())
                .swapTotalBytes(snapshot.getSwapTotalBytes())
                .diskUsedBytes(snapshot.getDiskUsedBytes())
                .diskTotalBytes(snapshot.getDiskTotalBytes())
                .diskUsagePercent(snapshot.getDiskUsagePercent())
                .networkRecvBytesPerSec(snapshot.getNetworkRecvBytesPerSec())
                .networkSentBytesPerSec(snapshot.getNetworkSentBytesPerSec())
                .jvmHeapUsedBytes(snapshot.getJvmHeapUsedBytes())
                .jvmHeapMaxBytes(snapshot.getJvmHeapMaxBytes())
                .jvmNonHeapUsedBytes(snapshot.getJvmNonHeapUsedBytes())
                .build();
    }
}
