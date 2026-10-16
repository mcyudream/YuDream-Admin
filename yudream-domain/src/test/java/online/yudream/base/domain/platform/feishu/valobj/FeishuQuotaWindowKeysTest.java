package online.yudream.base.domain.platform.feishu.valobj;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 额度窗口键：按上海时区分账，分钟与日窗口跨边界时同时切换。
 */
class FeishuQuotaWindowKeysTest {

    @Test
    void formatsMinuteAndDayWindowsInShanghaiZone() {
        FeishuQuotaWindowKeys keys = FeishuQuotaWindowKeys.of(Instant.parse("2026-01-01T15:59:59Z"));
        assertEquals("202601012359", keys.minuteKey());
        assertEquals("20260101", keys.dayKey());
    }

    @Test
    void rollsOverBothWindowsAtShanghaiMidnight() {
        FeishuQuotaWindowKeys keys = FeishuQuotaWindowKeys.of(Instant.parse("2026-01-01T16:00:00Z"));
        assertEquals("202601020000", keys.minuteKey());
        assertEquals("20260102", keys.dayKey());
    }

    @Test
    void minuteWindowChangesWithinTheSameDay() {
        FeishuQuotaWindowKeys first = FeishuQuotaWindowKeys.of(Instant.parse("2026-05-06T01:00:00Z"));
        FeishuQuotaWindowKeys second = FeishuQuotaWindowKeys.of(Instant.parse("2026-05-06T01:01:00Z"));
        assertEquals(first.dayKey(), second.dayKey());
        assertNotEquals(first.minuteKey(), second.minuteKey());
    }

    @Test
    void nullInstantFallsBackToNow() {
        FeishuQuotaWindowKeys keys = FeishuQuotaWindowKeys.of(null);
        assertEquals(12, keys.minuteKey().length());
        assertEquals(8, keys.dayKey().length());
    }
}
