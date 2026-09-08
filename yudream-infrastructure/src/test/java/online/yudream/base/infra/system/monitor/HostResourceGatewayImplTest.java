package online.yudream.base.infra.system.monitor;

import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostResourceGatewayImplTest {

    @Test
    void snapshotIncludesHostAndJvmMetrics() {
        HostResourceGatewayImpl gateway = new HostResourceGatewayImpl();

        HostResourceSnapshotDTO snapshot = gateway.snapshot();

        assertNotNull(snapshot.getSampledAt());
        assertEquals(HostResourceGatewayImpl.NOTICE, snapshot.getNotice());
        assertNotNull(snapshot.getCpuUsagePercent());
        assertTrue(snapshot.getCpuUsagePercent() >= 0 && snapshot.getCpuUsagePercent() <= 100);
        assertNotNull(snapshot.getMemoryTotalBytes());
        assertTrue(snapshot.getMemoryTotalBytes() > 0);
        assertNotNull(snapshot.getJvmHeapUsedBytes());
        assertFalse(snapshot.getMemoryPools().isEmpty());
        assertFalse(snapshot.getGarbageCollectors().isEmpty());
        assertNotNull(snapshot.getTopProcesses());
        assertNotNull(snapshot.getTopThreads());
    }
}
