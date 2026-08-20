package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryQqSandboxSessionRepoTest {

    @Test
    void expiresIdleSessionAndClearsResources() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        InMemoryQqSandboxSessionRepo repo = new InMemoryQqSandboxSessionRepo(
                Clock.fixed(now, ZoneOffset.UTC), 10);
        QqSandboxSession expired = session("expired", now.minusSeconds(31 * 60));
        expired.documentOverlay().put("history", new java.util.concurrent.ConcurrentHashMap<>(Map.of("1", Map.of("id", "1"))));
        repo.save(expired);
        expired.touch(now.minusSeconds(31 * 60));

        assertTrue(repo.findById("expired").isEmpty());
        assertEquals("CLOSED", expired.status());
        assertTrue(expired.documentOverlay().isEmpty());
    }

    @Test
    void evictsLeastRecentlyUsedSessionAtCapacity() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        InMemoryQqSandboxSessionRepo repo = new InMemoryQqSandboxSessionRepo(
                Clock.fixed(now, ZoneOffset.UTC), 1);
        QqSandboxSession first = session("first", now.minusSeconds(60));
        repo.save(first);
        first.touch(now.minusSeconds(60));
        QqSandboxSession second = session("second", now);
        repo.save(second);

        assertEquals(1, repo.size());
        assertEquals("CLOSED", first.status());
        assertTrue(repo.findById("second").isPresent());
    }

    private QqSandboxSession session(String id, Instant createdAt) {
        return QqSandboxSession.create(id, "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 1_000L, createdAt);
    }
}
