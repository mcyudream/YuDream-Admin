package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginAiInvocationContextGatewayTest {
    @Test
    void persistedContextRestoresOriginalSandboxAndFreshPermissionsWithoutRetainingOldRoles() throws Exception {
        var repo = new InMemoryQqSandboxSessionRepo(); var sandbox = sandbox(); repo.save(sandbox);
        var gateway = new PluginAiInvocationContextGateway(repo,new PluginAiToolRegistry());
        AiInvocationContextSnapshot snapshot;
        try (var scope = QqSandboxExecutionScope.open(sandbox)) {
            PluginAiToolExecutionScope.set(context(sandbox.connectionId(),List.of("read.one","write.one")));
            snapshot = gateway.capture();
        } finally { PluginAiToolExecutionScope.clear(); }
        assertEquals(sandbox.id(),snapshot.sandboxId());
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snapshot).contains("old:role:permission"));
        try (var scope = gateway.restore(snapshot,7L,List.of("fresh:permission"))) {
            assertSame(sandbox,QqSandboxExecutionScope.current());
            var restored = PluginAiToolExecutionScope.current();
            assertEquals(List.of("fresh:permission"),restored.permissions());
            assertEquals(List.of("write.one"),restored.grantedWriteToolNames());
            assertEquals("qq-user",restored.platformUserId()); assertEquals("channel",restored.channelId());
        }
        assertNull(QqSandboxExecutionScope.current()); assertNull(PluginAiToolExecutionScope.current());
    }

    @Test
    void missingOrClosedSandboxNeverFallsBackToAProductionConnection() {
        var repo = new InMemoryQqSandboxSessionRepo(); var sandbox = sandbox(); repo.save(sandbox);
        var snapshot = new AiInvocationContextSnapshot("qq-user",sandbox.connectionId(),"channel","message","MENTION","trace",List.of("read.one"),List.of(),sandbox.id());
        var gateway = new PluginAiInvocationContextGateway(repo,new PluginAiToolRegistry());
        sandbox.close();
        assertThrows(BizException.class,()->gateway.restore(snapshot,null,List.of("*")));
        repo.delete(sandbox.id());
        assertThrows(BizException.class,()->gateway.restore(snapshot,null,List.of("*")));
        var anotherInstance = new PluginAiInvocationContextGateway(new InMemoryQqSandboxSessionRepo(),new PluginAiToolRegistry());
        assertThrows(BizException.class,()->anotherInstance.restore(snapshot,null,List.of("*")));
        assertNull(PluginAiToolExecutionScope.current()); assertNull(QqSandboxExecutionScope.current());
    }

    @Test
    void wildcardIsCapturedAsCurrentNamedToolsAndExplicitSandboxConnectionCannotBecomeOrdinaryContext() {
        var registry = new PluginAiToolRegistry();
        register(registry, "read.one", "read:permission", PluginAiToolRisk.READ, "MENTION");
        register(registry, "read.denied", "missing:permission", PluginAiToolRisk.READ, "MENTION");
        register(registry, "read.other-trigger", "read:permission", PluginAiToolRisk.READ, "COMMAND");
        register(registry, "write.one", "read:permission", PluginAiToolRisk.WRITE, "MENTION");
        register(registry, "write.ungranted", "read:permission", PluginAiToolRisk.WRITE, "MENTION");
        var gateway = new PluginAiInvocationContextGateway(new InMemoryQqSandboxSessionRepo(),registry);
        PluginAiToolExecutionScope.set(context("42",List.of("*")));
        try {
            var captured = gateway.capture(); assertEquals(List.of("read.one", "write.one"),captured.allowedToolNames());
            assertNull(captured.sandboxId());
            PluginAiToolExecutionScope.set(context("devtools-sandbox:missing",List.of("*")));
            assertThrows(BizException.class,gateway::capture);
        } finally { PluginAiToolExecutionScope.clear(); }
        assertNull(gateway.capture());
    }

    private static void register(PluginAiToolRegistry registry, String name, String permission, PluginAiToolRisk risk, String trigger) {
        var tool = mock(PluginAiTool.class);
        when(tool.descriptor()).thenReturn(new PluginAiToolDescriptor(name,"工具","说明",permission,risk,false,Set.of(trigger),Map.of()));
        registry.register("plugin",tool);
    }
    private static PluginAiExecutionContext context(String connection,List<String> tools) {
        return new PluginAiExecutionContext(7L,"qq-user",connection,"channel","message","MENTION","trace",List.of("old:role:permission","read:permission"),tools,List.of("write.one"));
    }
    private static QqSandboxSession sandbox() {
        return QqSandboxSession.create("sandbox","plugin","42","self","qq-user",null,"channel","group",QqSandboxRandomMode.REAL,120_000,Instant.now());
    }
}
