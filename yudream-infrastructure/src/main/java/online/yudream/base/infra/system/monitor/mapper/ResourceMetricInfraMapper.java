package online.yudream.base.infra.system.monitor.mapper;

import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import online.yudream.base.infra.system.monitor.dataobj.ResourceMetricDO;

public final class ResourceMetricInfraMapper {

    private ResourceMetricInfraMapper() {
    }

    public static ResourceMetricDO toDataObj(ResourceMetricPointDTO point) {
        if (point == null) {
            return null;
        }
        ResourceMetricDO data = new ResourceMetricDO();
        data.setSampledAt(point.getSampledAt());
        data.setCpuUsagePercent(point.getCpuUsagePercent());
        data.setMemoryUsedBytes(point.getMemoryUsedBytes());
        data.setMemoryTotalBytes(point.getMemoryTotalBytes());
        data.setMemoryUsagePercent(point.getMemoryUsagePercent());
        data.setSwapUsedBytes(point.getSwapUsedBytes());
        data.setSwapTotalBytes(point.getSwapTotalBytes());
        data.setDiskUsedBytes(point.getDiskUsedBytes());
        data.setDiskTotalBytes(point.getDiskTotalBytes());
        data.setDiskUsagePercent(point.getDiskUsagePercent());
        data.setNetworkRecvBytesPerSec(point.getNetworkRecvBytesPerSec());
        data.setNetworkSentBytesPerSec(point.getNetworkSentBytesPerSec());
        data.setJvmHeapUsedBytes(point.getJvmHeapUsedBytes());
        data.setJvmHeapMaxBytes(point.getJvmHeapMaxBytes());
        data.setJvmNonHeapUsedBytes(point.getJvmNonHeapUsedBytes());
        return data;
    }

    public static ResourceMetricPointDTO toDto(ResourceMetricDO data) {
        if (data == null) {
            return null;
        }
        return ResourceMetricPointDTO.builder()
                .sampledAt(data.getSampledAt())
                .cpuUsagePercent(data.getCpuUsagePercent())
                .memoryUsedBytes(data.getMemoryUsedBytes())
                .memoryTotalBytes(data.getMemoryTotalBytes())
                .memoryUsagePercent(data.getMemoryUsagePercent())
                .swapUsedBytes(data.getSwapUsedBytes())
                .swapTotalBytes(data.getSwapTotalBytes())
                .diskUsedBytes(data.getDiskUsedBytes())
                .diskTotalBytes(data.getDiskTotalBytes())
                .diskUsagePercent(data.getDiskUsagePercent())
                .networkRecvBytesPerSec(data.getNetworkRecvBytesPerSec())
                .networkSentBytesPerSec(data.getNetworkSentBytesPerSec())
                .jvmHeapUsedBytes(data.getJvmHeapUsedBytes())
                .jvmHeapMaxBytes(data.getJvmHeapMaxBytes())
                .jvmNonHeapUsedBytes(data.getJvmNonHeapUsedBytes())
                .build();
    }
}
