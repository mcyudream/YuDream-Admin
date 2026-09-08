package online.yudream.base.application.system.monitor;

import online.yudream.base.application.system.monitor.service.HostResourceAppService;
import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;
import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import online.yudream.base.domain.system.monitor.repo.ResourceMetricRepo;
import online.yudream.base.domain.system.monitor.service.HostResourceGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class HostResourceAppServiceTest {

    @Test
    void snapshotDelegatesToGateway() {
        HostResourceSnapshotDTO snapshot = HostResourceSnapshotDTO.builder().hostname("box").build();
        HostResourceAppService service = new HostResourceAppService(() -> snapshot, new RecordingRepo());

        assertSame(snapshot, service.snapshot());
    }

    @Test
    void historyFallsBackToOneHourWhenWindowIsInvalid() {
        RecordingRepo repo = new RecordingRepo();
        HostResourceAppService service = new HostResourceAppService(() -> HostResourceSnapshotDTO.builder().build(), repo);

        service.history(3);

        assertNearHoursAgo(repo.lastFrom.get(), 1);
    }

    @Test
    void historyKeepsAllowedWindows() {
        RecordingRepo repo = new RecordingRepo();
        HostResourceAppService service = new HostResourceAppService(() -> HostResourceSnapshotDTO.builder().build(), repo);

        service.history(24);

        assertNearHoursAgo(repo.lastFrom.get(), 24);
    }

    @Test
    void recordSamplePersistsScalarPointFromSnapshot() {
        HostResourceSnapshotDTO snapshot = HostResourceSnapshotDTO.builder()
                .sampledAt(LocalDateTime.of(2026, 9, 7, 12, 0))
                .cpuUsagePercent(12.5)
                .memoryUsedBytes(1024L)
                .memoryTotalBytes(2048L)
                .memoryUsagePercent(50.0)
                .diskUsedBytes(10L)
                .diskTotalBytes(100L)
                .diskUsagePercent(10.0)
                .networkRecvBytesPerSec(8L)
                .networkSentBytesPerSec(4L)
                .jvmHeapUsedBytes(256L)
                .jvmHeapMaxBytes(512L)
                .jvmNonHeapUsedBytes(64L)
                .build();
        RecordingRepo repo = new RecordingRepo();
        HostResourceAppService service = new HostResourceAppService(() -> snapshot, repo);

        service.recordSample();

        ResourceMetricPointDTO saved = repo.saved.get();
        assertEquals(snapshot.getSampledAt(), saved.getSampledAt());
        assertEquals(12.5, saved.getCpuUsagePercent());
        assertEquals(1024L, saved.getMemoryUsedBytes());
        assertEquals(256L, saved.getJvmHeapUsedBytes());
        assertEquals(8L, saved.getNetworkRecvBytesPerSec());
    }

    private static void assertNearHoursAgo(LocalDateTime from, int hours) {
        LocalDateTime now = LocalDateTime.now();
        assertFalse(from.isBefore(now.minusHours(hours).minusMinutes(1)));
        assertFalse(from.isAfter(now.minusHours(hours).plusMinutes(1)));
    }

    private static final class RecordingRepo implements ResourceMetricRepo {
        private final AtomicReference<ResourceMetricPointDTO> saved = new AtomicReference<>();
        private final AtomicReference<LocalDateTime> lastFrom = new AtomicReference<>();

        @Override
        public void save(ResourceMetricPointDTO point) {
            saved.set(point);
        }

        @Override
        public List<ResourceMetricPointDTO> findSince(LocalDateTime from) {
            lastFrom.set(from);
            return new ArrayList<>();
        }
    }
}
