package online.yudream.base.infra.platform.plugin.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxTimelineEvent;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSandboxErrorDiagnosticsTest {

    @Test
    void capturesInteractionHandlerErrorIntoSandboxTimeline() throws Exception {
        PluginMessageInteractionRegistryImpl registry = new PluginMessageInteractionRegistryImpl("demo");
        try {
            registry.onMessage(null, event -> {
                throw new IllegalStateException("handler boom");
            });
            QqSandboxSession session = session("handler", false);
            try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
                registry.publish(event(), false);
                QqSandboxExecutionScope.awaitTracked().toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            QqSandboxTimelineEvent error = session.timeline().stream()
                    .filter(item -> "handler.error".equals(item.action()))
                    .findFirst().orElseThrow();
            assertEquals("runtime", error.phase());
            assertEquals("demo", error.pluginCode());
            assertEquals("java.lang.IllegalStateException", error.payload().get("errorType"));
            assertEquals("handler boom", error.payload().get("message"));
            assertTrue(String.valueOf(error.payload().get("stackTrace")).contains("IllegalStateException"));
            assertEquals("MESSAGE", error.payload().get("kind"));
        } finally {
            registry.close();
        }
    }

    @Test
    void swallowsHandlerErrorWithoutSandboxScope() throws Exception {
        PluginMessageInteractionRegistryImpl registry = new PluginMessageInteractionRegistryImpl("demo");
        try {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            registry.onMessage(null, event -> {
                throw new IllegalStateException("silent");
            });
            registry.onMessage(null, event -> latch.countDown());
            registry.publish(event(), false);
            assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            registry.close();
        }
    }

    @Test
    void bridgesPluginErrorLogsIntoSandboxTimeline() {
        LoggerContext context = new LoggerContext();
        QqSandboxLogAppender appender = new QqSandboxLogAppender();
        appender.setContext(context);
        appender.start();
        ch.qos.logback.classic.Logger pluginLogger = context.getLogger("online.yudream.base.plugin.demo.Bootstrap");
        pluginLogger.addAppender(appender);
        pluginLogger.setLevel(Level.DEBUG);

        QqSandboxSession session = session("logs", false);
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            pluginLogger.warn("插件警告 {}", "x");
            pluginLogger.error("插件失败", new IllegalArgumentException("log boom"));
        }

        QqSandboxTimelineEvent error = session.timeline().stream()
                .filter(item -> "log.error".equals(item.action()))
                .findFirst().orElseThrow();
        assertEquals("log", error.phase());
        assertEquals("demo", error.pluginCode());
        assertEquals("插件失败", error.payload().get("message"));
        assertTrue(String.valueOf(error.payload().get("stackTrace")).contains("log boom"));
        QqSandboxTimelineEvent warn = session.timeline().stream()
                .filter(item -> "log.warn".equals(item.action()))
                .findFirst().orElseThrow();
        assertEquals("插件警告 x", warn.payload().get("message"));
        assertFalse(warn.payload().containsKey("stackTrace"));
    }

    @Test
    void ignoresHostLogsAndInactiveScope() {
        LoggerContext context = new LoggerContext();
        QqSandboxLogAppender appender = new QqSandboxLogAppender();
        appender.setContext(context);
        appender.start();
        ch.qos.logback.classic.Logger hostLogger = context.getLogger("online.yudream.base.infra.platform.Foo");
        hostLogger.addAppender(appender);
        ch.qos.logback.classic.Logger pluginLogger = context.getLogger("online.yudream.base.plugin.demo.Bootstrap");
        pluginLogger.addAppender(appender);

        QqSandboxSession session = session("host", false);
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            hostLogger.error("宿主日志不应入沙盒");
            pluginLogger.info("INFO 级别不应入沙盒");
        }
        assertTrue(session.timeline().isEmpty());

        pluginLogger.error("作用域外日志不应入沙盒");
        assertTrue(session.timeline().isEmpty());
    }

    @Test
    void derivesPluginCodeFromLoggerName() {
        assertEquals("wordle", QqSandboxLogAppender.pluginCodeOf("online.yudream.base.plugin.wordle.WordlePlugin"));
        assertEquals("demo", QqSandboxLogAppender.pluginCodeOf("online.yudream.base.plugin.demo"));
    }

    @Test
    void skipsCaptureWhenSessionClosed() {
        QqSandboxSession session = session("closed", false);
        session.close();
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            QqSandboxDiagnostics.appendError("handler.error", "demo", new IllegalStateException("boom"), null);
        }
        assertTrue(session.timeline().isEmpty());
    }

    private static QqSandboxSession session(String id, boolean forceUnbound) {
        return QqSandboxSession.create(id, "demo", "1", "10000", "10001", "Tester", "20001", "group",
                QqSandboxRandomMode.REAL, forceUnbound, null, 60_000L, Instant.now());
    }

    private static PluginEvent event() {
        return new PluginEvent("1", "message", "milky", "10001", "20001", "你好", null, null,
                Map.of(), "message_receive", Map.of(), "devtools-sandbox:t", "10000", "m1");
    }
}
