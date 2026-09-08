package online.yudream.base.infra.system.monitor.mapper;

import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceMetricInfraMapperTest {

    @Test
    void mapsNullAsNull() {
        assertNull(ResourceMetricInfraMapper.toDataObj(null));
        assertNull(ResourceMetricInfraMapper.toDto(null));
    }

    @Test
    void roundTripsScalarPoint() {
        ResourceMetricPointDTO point = ResourceMetricPointDTO.builder()
                .sampledAt(LocalDateTime.of(2026, 9, 7, 8, 30))
                .cpuUsagePercent(33.3)
                .memoryUsedBytes(100L)
                .memoryTotalBytes(200L)
                .memoryUsagePercent(50.0)
                .swapUsedBytes(1L)
                .swapTotalBytes(2L)
                .diskUsedBytes(10L)
                .diskTotalBytes(20L)
                .diskUsagePercent(50.0)
                .networkRecvBytesPerSec(8L)
                .networkSentBytesPerSec(4L)
                .jvmHeapUsedBytes(64L)
                .jvmHeapMaxBytes(128L)
                .jvmNonHeapUsedBytes(16L)
                .build();

        ResourceMetricPointDTO restored = ResourceMetricInfraMapper.toDto(ResourceMetricInfraMapper.toDataObj(point));

        assertEquals(point.getSampledAt(), restored.getSampledAt());
        assertEquals(point.getCpuUsagePercent(), restored.getCpuUsagePercent());
        assertEquals(point.getMemoryUsedBytes(), restored.getMemoryUsedBytes());
        assertEquals(point.getJvmHeapUsedBytes(), restored.getJvmHeapUsedBytes());
        assertEquals(point.getNetworkSentBytesPerSec(), restored.getNetworkSentBytesPerSec());
    }
}
