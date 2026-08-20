package online.yudream.base.domain.platform.milky.sandbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QqSandboxSessionTest {

    @Test
    void generatesSyntheticStringConnectionAndKeepsRandomMode() {
        for (QqSandboxRandomMode mode : QqSandboxRandomMode.values()) {
            QqSandboxSession session = QqSandboxSession.create("session-" + mode, "demo", "1", "10001", "20002",
                    null, "30003", "group", mode, 3_000L, Instant.parse("2026-08-20T00:00:00Z"));

            assertEquals("devtools-sandbox:" + session.id(), session.connectionId());
            assertEquals(mode, session.randomMode());
            assertNull(session.nickname());
        }
    }

    @Test
    void trimsAndKeepsNickname() {
        QqSandboxSession session = QqSandboxSession.create("session-nick", "demo", "1", "10001", "20002",
                "  沙盒用户  ", "30003", "group", QqSandboxRandomMode.REAL, 3_000L,
                Instant.parse("2026-08-20T00:00:00Z"));

        assertEquals("沙盒用户", session.nickname());
    }

    @Test
    void keepsTimelineOrdered() {
        QqSandboxSession session = QqSandboxSession.create("session-1", "demo", "1", "10001", "20002", null, "30003",
                "group", QqSandboxRandomMode.REAL, 3_000L, Instant.parse("2026-08-20T00:00:00Z"));

        session.append("input", "message", "demo", Map.of("messageSeq", "9007199254740995"));
        session.append("output", "messaging.send", "demo", Map.of("messageId", "9007199254740997"));

        assertEquals(1L, session.timeline().getFirst().sequence());
        assertEquals(2L, session.timeline().getLast().sequence());
    }

    @Test
    void brokenListenerIsRemovedAndNeverBlocksAppend() {
        QqSandboxSession session = QqSandboxSession.create("session-listener", "demo", "1", "10001", "20002", null,
                "30003", "group", QqSandboxRandomMode.REAL, 3_000L, Instant.parse("2026-08-20T00:00:00Z"));
        AtomicInteger brokenCalls = new AtomicInteger();
        AtomicInteger healthyCalls = new AtomicInteger();
        session.subscribe(event -> {
            brokenCalls.incrementAndGet();
            throw new IllegalStateException("response already committed");
        });
        session.subscribe(event -> healthyCalls.incrementAndGet());

        session.append("input", "message.synthetic", "demo", Map.of());
        session.append("output", "messaging.send", "demo", Map.of());
        session.append("session", "dispatch.completed", "demo", Map.of());

        assertEquals(3, session.timeline().size());
        assertEquals(1, brokenCalls.get());
        assertEquals(3, healthyCalls.get());
    }

    @Test
    void rejectsUnsupportedRandomMode() {
        assertThrows(IllegalArgumentException.class, () -> QqSandboxRandomMode.from("RANDOM"));
    }
}
