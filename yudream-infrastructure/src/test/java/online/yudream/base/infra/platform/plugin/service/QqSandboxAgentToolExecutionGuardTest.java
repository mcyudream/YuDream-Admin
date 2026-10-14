package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class QqSandboxAgentToolExecutionGuardTest {

    @Test
    void recordsAllowedAndRejectedTools() {
        QqSandboxSession session = QqSandboxSession.create("guard", "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 1_000L, Instant.now());
        QqSandboxAgentToolExecutionGuard guard = new QqSandboxAgentToolExecutionGuard();

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            guard.check("wiki.search", AgentToolRisk.READ, "wiki:read");
            assertThrows(RuntimeException.class, () -> guard.check("python_tool", AgentToolRisk.WRITE, "tool:write"));
        }

        assertTrue(session.timeline().stream().anyMatch(event -> "tool.allowed".equals(event.action())));
        assertTrue(session.timeline().stream().anyMatch(event -> "tool.rejected".equals(event.action())));
    }

    @Test
    void capturedGuardBindsEntireNativeWorkerActionAndRejectsWritesOrClosedSandbox() throws Exception {
        QqSandboxSession session = QqSandboxSession.create("native-guard", "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 60_000L, Instant.now());
        online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard captured;
        try (var ignored = QqSandboxExecutionScope.open(session)) {
            captured = new QqSandboxAgentToolExecutionGuard().capture();
        }
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            assertTrue(executor.submit(() -> {
                assertNull(QqSandboxExecutionScope.current());
                captured.execute("read", AgentToolRisk.READ, "view", () -> {
                    assertSame(session, QqSandboxExecutionScope.requireActive());
                    return true;
                });
                assertNull(QqSandboxExecutionScope.current(), "原生线程返回后不能泄漏沙箱上下文");
                assertThrows(RuntimeException.class, () -> captured.execute("write", AgentToolRisk.WRITE, "edit", () -> {
                    throw new AssertionError("沙箱写入不能执行");
                }));
                return true;
            }).get(5, java.util.concurrent.TimeUnit.SECONDS));
            session.close();
            assertTrue(executor.submit(() -> {
                assertThrows(RuntimeException.class, () -> captured.execute("read", AgentToolRisk.READ, "view", () -> {
                    throw new AssertionError("已关闭沙箱不能回落真实执行");
                }));
                return true;
            }).get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
