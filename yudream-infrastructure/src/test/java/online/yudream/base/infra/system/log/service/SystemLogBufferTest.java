package online.yudream.base.infra.system.log.service;

import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.domain.system.log.model.SystemLogLevel;
import online.yudream.base.domain.system.log.model.SystemLogQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemLogBufferTest {

    @Test
    void appendsAndReturnsNewestFirst() {
        SystemLogBuffer buffer = SystemLogBuffer.instance();
        buffer.clear();
        buffer.append(1000L, SystemLogLevel.INFO, "a", "系统", "t", null, "one", null);
        buffer.append(2000L, SystemLogLevel.ERROR, "b", "Milky 消息平台", "t", null, "two", null);
        List<SystemLogEntry> recent = buffer.recent(SystemLogQuery.of(null, Set.of(), null, 10));
        assertEquals(2, recent.size());
        assertEquals("two", recent.get(0).message());
    }

    @Test
    void filtersByLevelModuleAndKeyword() {
        SystemLogBuffer buffer = SystemLogBuffer.instance();
        buffer.clear();
        buffer.append(1L, SystemLogLevel.INFO, "a", "系统", "t", null, "hello world", null);
        buffer.append(2L, SystemLogLevel.ERROR, "b", "Milky 消息平台", "t", null, "boom error", null);
        assertEquals(1, buffer.recent(SystemLogQuery.of("ERROR", Set.of(), null, 10)).size());
        assertEquals(1, buffer.recent(SystemLogQuery.of(null, Set.of("Milky 消息平台"), null, 10)).size());
        assertEquals(1, buffer.recent(SystemLogQuery.of(null, Set.of(), "hello", 10)).size());
    }

    @Test
    void subscribesMatchingEntries() throws Exception {
        SystemLogBuffer buffer = SystemLogBuffer.instance();
        buffer.clear();
        AtomicInteger count = new AtomicInteger();
        try (AutoCloseable subscription = buffer.subscribe(SystemLogQuery.of("ERROR", Set.of(), null, 10), entry -> count.incrementAndGet())) {
            buffer.append(1L, SystemLogLevel.INFO, "a", "系统", "t", null, "info", null);
            buffer.append(2L, SystemLogLevel.ERROR, "b", "系统", "t", null, "err", null);
        }
        assertEquals(1, count.get());
    }

    @Test
    void countsEntriesByLevelForEveryLevel() {
        SystemLogBuffer buffer = SystemLogBuffer.instance();
        buffer.clear();
        buffer.append(1L, SystemLogLevel.INFO, "a", "系统", "t", null, "one", null);
        buffer.append(2L, SystemLogLevel.INFO, "a", "系统", "t", null, "two", null);
        buffer.append(3L, SystemLogLevel.WARN, "a", "系统", "t", null, "three", null);
        buffer.append(4L, SystemLogLevel.ERROR, "a", "系统", "t", null, "four", null);

        Map<String, Long> counts = buffer.levelCounts();
        // 五个级别都出现在结果里，缺计数为 0，前端胶囊才能稳定渲染
        assertEquals(SystemLogLevel.values().length, counts.size());
        assertEquals(2L, counts.get("INFO"));
        assertEquals(1L, counts.get("WARN"));
        assertEquals(1L, counts.get("ERROR"));
        assertEquals(0L, counts.get("TRACE"));
        assertEquals(0L, counts.get("DEBUG"));
        buffer.clear();
    }

    @Test
    void evictsOldestBeyondMaxAndCountsDropped() {
        SystemLogBuffer buffer = SystemLogBuffer.instance();
        buffer.clear();
        int previousMax = buffer.maxEntries();
        buffer.setMaxEntries(100);
        try {
            for (int i = 0; i < 150; i++) {
                buffer.append(i, SystemLogLevel.INFO, "a", "系统", "t", null, "m" + i, null);
            }
            assertEquals(100, buffer.size());
            assertTrue(buffer.droppedCount() > 0);
        } finally {
            buffer.setMaxEntries(previousMax);
            buffer.clear();
        }
    }
}
