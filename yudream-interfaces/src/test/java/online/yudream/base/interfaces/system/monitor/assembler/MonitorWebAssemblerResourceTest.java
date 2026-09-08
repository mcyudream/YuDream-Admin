package online.yudream.base.interfaces.system.monitor.assembler;

import online.yudream.base.domain.system.monitor.dto.HostProcessDTO;
import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;
import online.yudream.base.domain.system.monitor.dto.JvmMemoryPoolDTO;
import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorWebAssemblerResourceTest {

    @Test
    void mapsSnapshotNestedCollections() {
        HostResourceSnapshotDTO dto = HostResourceSnapshotDTO.builder()
                .hostname("box")
                .cpuUsagePercent(21.5)
                .memoryPools(List.of(JvmMemoryPoolDTO.builder().name("G1 Eden").usedBytes(8L).build()))
                .topProcesses(List.of(HostProcessDTO.builder().pid(9).name("java").currentJvm(true).build()))
                .sampledAt(LocalDateTime.of(2026, 9, 7, 10, 0))
                .build();

        var res = MonitorWebAssembler.toRes(dto);

        assertEquals("box", res.getHostname());
        assertEquals(21.5, res.getCpuUsagePercent());
        assertEquals("G1 Eden", res.getMemoryPools().getFirst().getName());
        assertTrue(res.getTopProcesses().getFirst().isCurrentJvm());
        assertEquals(List.of(), res.getTopThreads());
    }

    @Test
    void mapsHistoryPoints() {
        ResourceMetricPointDTO point = ResourceMetricPointDTO.builder()
                .sampledAt(LocalDateTime.of(2026, 9, 7, 10, 1))
                .cpuUsagePercent(8.0)
                .jvmHeapUsedBytes(32L)
                .build();

        var res = MonitorWebAssembler.toResourceHistoryRes(List.of(point));

        assertEquals(1, res.size());
        assertEquals(8.0, res.getFirst().getCpuUsagePercent());
        assertEquals(32L, res.getFirst().getJvmHeapUsedBytes());
    }
}
