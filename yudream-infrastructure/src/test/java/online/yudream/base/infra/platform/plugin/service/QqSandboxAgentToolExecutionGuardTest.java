package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
