package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QqSandboxExecutionScopeTest {

    @Test
    void propagatesCapturedContextAcrossAsyncBoundaryAndRestoresPreviousScope() {
        QqSandboxSession session = session("sandbox");

        Runnable task;
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            task = QqSandboxExecutionScope.wrap((Runnable)
                    () -> session.append("handler", "async", "demo", java.util.Map.of()));
        }
        assertNull(QqSandboxExecutionScope.current());

        CompletableFuture.runAsync(task).join();

        assertEquals(1, session.timeline().size());
        assertNull(QqSandboxExecutionScope.current());
    }

    @Test
    void reusesOverlayStateAcrossMultipleDispatchScopesForSameSession() {
        QqSandboxSession session = session("conversation");
        Map<String, Map<String, Map<String, Object>>> first;
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            first = QqSandboxExecutionScope.documents();
            first.computeIfAbsent("history", key -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put("group", Map.of("content", "first"));
        }

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertSame(first, QqSandboxExecutionScope.documents());
            assertEquals("first", QqSandboxExecutionScope.documents().get("history").get("group").get("content"));
        }
    }

    @Test
    void cancellingAwaitCancelsPendingStagesAndClosesSession() throws Exception {
        QqSandboxSession session = session("cancel");
        CompletableFuture<Void> pending = new CompletableFuture<>();
        CompletableFuture<Void> waiting;
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            QqSandboxExecutionScope.track(pending);
            waiting = QqSandboxExecutionScope.awaitTracked().toCompletableFuture();
        }

        waiting.cancel(true);
        Thread.sleep(20L);

        assertEquals(true, pending.isCancelled());
        assertEquals("CLOSED", session.status());
        assertEquals(false, session.acceptsCaptures());
    }

    @Test
    void waitsForTerminalDiagnosticActivity() throws Exception {
        QqSandboxSession session = session("activity");
        java.util.concurrent.CompletionStage<Void> waiting;
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            session.beginOperation("trace-1");
            waiting = QqSandboxExecutionScope.awaitTracked();
        }

        Thread.sleep(30L);
        assertEquals(false, waiting.toCompletableFuture().isDone());
        session.finishOperation("trace-1");
        waiting.toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void restrictsSandboxDispatchToSelectedPlugin() {
        QqSandboxSession session = session("scope");

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertEquals(true, QqSandboxExecutionScope.accepts("demo"));
            assertEquals(false, QqSandboxExecutionScope.accepts("other"));
        }
    }

    @Test
    void emptyPluginScopeBroadcastsToEveryPlugin() {
        QqSandboxSession session = QqSandboxSession.create("scope-all", null, "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 1_000L, Instant.now());

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertEquals(true, QqSandboxExecutionScope.accepts("demo"));
            assertEquals(true, QqSandboxExecutionScope.accepts("other"));
        }
    }

    @Test
    void rejectsLateCapturesAfterSessionTimeout() {
        QqSandboxSession session = session("timed-out");
        session.timedOut();

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertThrows(RuntimeException.class, QqSandboxExecutionScope::requireActive);
        }
    }

    private QqSandboxSession session(String id) {
        return QqSandboxSession.create(id, "demo", "1", "2", "3", null, "4", "group", QqSandboxRandomMode.REAL,
                1_000L, Instant.now());
    }
}
