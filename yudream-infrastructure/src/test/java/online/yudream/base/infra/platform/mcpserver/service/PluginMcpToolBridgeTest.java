package online.yudream.base.infra.platform.mcpserver.service;

import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolCall;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolResult;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMcpToolBridgeTest {

    /** 可编程 fake 工具：执行时记录 scope 状态与收到的上下文。 */
    private static final class StubTool implements PluginAiTool {
        private final PluginAiToolDescriptor descriptor;
        PluginAiExecutionContext seenContext;
        boolean scopePresentDuringExecute;
        RuntimeException failure;
        PluginAiToolResult result = new PluginAiToolResult("done", "完成", Map.of());

        StubTool(PluginAiToolDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public PluginAiToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public PluginAiToolResult execute(PluginAiExecutionContext context, PluginAiToolCall call) {
            this.seenContext = context;
            this.scopePresentDuringExecute = PluginAiToolExecutionScope.current() != null;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static StubTool tool(String name, PluginAiToolRisk risk, boolean requiresConfirmation,
                                 Set<String> triggers) {
        return new StubTool(new PluginAiToolDescriptor(name, name, "工具 " + name,
                "plugin:mcpanel:" + (risk == PluginAiToolRisk.READ ? "view" : "manage"),
                risk, requiresConfirmation, triggers, Map.of("type", "object")));
    }

    private static PluginMcpToolBridge bridge(StubTool... tools) {
        PluginAiToolRegistry registry = new PluginAiToolRegistry();
        for (StubTool t : tools) {
            registry.register("mcpanel", t);
        }
        return new PluginMcpToolBridge(registry, null);
    }

    private static final McpToolPrincipal PRINCIPAL = new McpToolPrincipal(7L, List.of("plugin:mcpanel:view"));

    @Test
    void exposesOnlyMcpTriggeredNonDestructiveNonConfirmTools() {
        PluginMcpToolBridge bridge = bridge(
                tool("mcpanel_read", PluginAiToolRisk.READ, false, Set.of("MENTION", "MCP")),
                tool("mcpanel_write", PluginAiToolRisk.WRITE, false, Set.of("MCP")),
                tool("mcpanel_destructive", PluginAiToolRisk.DESTRUCTIVE, false, Set.of("MCP")),
                tool("mcpanel_confirm", PluginAiToolRisk.READ, true, Set.of("MCP")),
                tool("mcpanel_mention_only", PluginAiToolRisk.READ, false, Set.of("MENTION")));

        List<McpExposedTool> exposed = bridge.exposedTools("mcpanel");
        assertEquals(List.of("mcpanel_read", "mcpanel_write"),
                exposed.stream().map(McpExposedTool::name).toList());
        assertTrue(exposed.get(1).write());
        assertFalse(exposed.get(0).write());

        assertEquals(1, bridge.modules().size());
        assertEquals("mcpanel", bridge.modules().get(0).code());
        // runtimeGateway 为 null 时显示名回退为插件 code
        assertEquals("mcpanel", bridge.modules().get(0).displayName());
    }

    @Test
    void modulesSkipPluginsWithoutExposableTools() {
        PluginAiToolRegistry registry = new PluginAiToolRegistry();
        registry.register("chatonly", tool("chat_tool", PluginAiToolRisk.READ, false, Set.of("MENTION")));
        PluginMcpToolBridge bridge = new PluginMcpToolBridge(registry, null);
        assertTrue(bridge.modules().isEmpty());
        assertTrue(bridge.exposedTools("chatonly").isEmpty());
    }

    @Test
    void callGrantsWriteToolAndCarriesIdentityViaScope() {
        StubTool write = tool("mcpanel_write", PluginAiToolRisk.WRITE, false, Set.of("MCP"));
        PluginMcpToolBridge bridge = bridge(write);

        McpToolOutcome outcome = bridge.call("mcpanel", "mcpanel_write", Map.of("id", "inst-1"), PRINCIPAL);

        assertFalse(outcome.error());
        assertTrue(write.scopePresentDuringExecute);
        assertEquals("MCP", write.seenContext.trigger());
        assertEquals(7L, write.seenContext.userId());
        assertEquals(List.of("plugin:mcpanel:view"), write.seenContext.permissions());
        assertTrue(write.seenContext.grantsWriteTool("mcpanel_write"));
        assertTrue(write.seenContext.allowsTool("mcpanel_write"));
        // 用后恢复：无前序上下文时清空
        assertNull(PluginAiToolExecutionScope.current());
        assertEquals("完成", outcome.text());
    }

    @Test
    void callRestoresPreviousScopeAndFoldsPayload() {
        StubTool read = tool("mcpanel_read", PluginAiToolRisk.READ, false, Set.of("MCP"));
        read.result = new PluginAiToolResult("listed", "列表如下", Map.of("count", 2));
        PluginMcpToolBridge bridge = bridge(read);

        PluginAiExecutionContext previous = new PluginAiExecutionContext(1L, "1", null, null, null,
                "MENTION", null, List.of("*"));
        PluginAiToolExecutionScope.set(previous);
        try {
            McpToolOutcome outcome = bridge.call("mcpanel", "mcpanel_read", Map.of(), PRINCIPAL);
            assertFalse(outcome.error());
            assertTrue(outcome.text().contains("列表如下"));
            assertTrue(outcome.text().contains("\"count\":2"));
            assertEquals(previous, PluginAiToolExecutionScope.current());
        } finally {
            PluginAiToolExecutionScope.clear();
        }
    }

    @Test
    void callMapsFailureToErrorOutcome() {
        StubTool failing = tool("mcpanel_fail", PluginAiToolRisk.READ, false, Set.of("MCP"));
        failing.failure = new RuntimeException("实例不存在");
        PluginMcpToolBridge bridge = bridge(failing);

        McpToolOutcome outcome = bridge.call("mcpanel", "mcpanel_fail", Map.of(), PRINCIPAL);
        assertTrue(outcome.error());
        assertEquals("实例不存在", outcome.text());
        assertNull(PluginAiToolExecutionScope.current());

        assertEquals(true, bridge.call("mcpanel", "no_such", Map.of(), PRINCIPAL).error());
    }
}
