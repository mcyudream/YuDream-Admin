package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.tool.ToolCallParam;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolCall;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolResult;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginAiToolAdapterTest {

    private static PluginAiExecutionContext context(String... writeTools) {
        return new PluginAiExecutionContext(
                1L, null, "conn-1", "chan-1", "msg-1", "MENTION", "trace-1",
                List.of("*"), List.of("*"), List.of(writeTools));
    }

    @Test
    void readToolGrantedByDefaultWriteToolRequiresExplicitGrant() {
        PluginAiTool read = pluginTool("qbank.search", PluginAiToolRisk.READ, false);
        PluginAiTool write = pluginTool("qbank.import", PluginAiToolRisk.WRITE, false);

        assertTrue(new PluginAiToolAdapter(read, context()).granted());
        assertFalse(new PluginAiToolAdapter(write, context()).granted());
        assertTrue(new PluginAiToolAdapter(write, context("qbank.import")).granted());
    }

    @Test
    void triggerMismatchBlocksGrant() {
        PluginAiTool read = pluginTool("qbank.search", PluginAiToolRisk.READ, false);
        PluginAiExecutionContext otherTrigger = new PluginAiExecutionContext(
                1L, null, null, null, null, "COMMAND", "trace-1", List.of(), List.of(), List.of());
        assertFalse(new PluginAiToolAdapter(read, otherTrigger).granted());
    }

    @Test
    void destructiveToolAsksForUserConfirmation() {
        PluginAiTool destructive = pluginTool("qbank.wipe", PluginAiToolRisk.DESTRUCTIVE, false);
        var decision = new PluginAiToolAdapter(destructive, context("qbank.wipe"))
                .checkPermissions(Map.of(), null).block();
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
    }

    @Test
    void requiresConfirmationFlagAsksEvenForWrite() {
        PluginAiTool write = pluginTool("qbank.import", PluginAiToolRisk.WRITE, true);
        var decision = new PluginAiToolAdapter(write, context("qbank.import"))
                .checkPermissions(Map.of(), null).block();
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
    }

    @Test
    void ungrantedExecutionReturnsDenied() {
        PluginAiTool write = pluginTool("qbank.import", PluginAiToolRisk.WRITE, false);
        PluginAiToolAdapter adapter = new PluginAiToolAdapter(write, context());
        var result = adapter.callAsync(param()).block();
        assertEquals(ToolResultState.DENIED, result.getState());
    }

    @Test
    void grantedExecutionReturnsPluginResult() {
        PluginAiTool write = pluginTool("qbank.import", PluginAiToolRisk.WRITE, false);
        PluginAiToolAdapter adapter = new PluginAiToolAdapter(write, context("qbank.import"));
        var result = adapter.callAsync(param()).block();
        assertEquals(ToolResultState.SUCCESS, result.getState());
    }

    private ToolCallParam param() {
        return ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder().id("call-1").name("tool").build())
                .input(Map.of())
                .build();
    }

    private PluginAiTool pluginTool(String name, PluginAiToolRisk risk, boolean requiresConfirmation) {
        return new PluginAiTool() {
            @Override
            public PluginAiToolDescriptor descriptor() {
                return new PluginAiToolDescriptor(
                        name, name, name + " 描述", null, risk, requiresConfirmation,
                        Set.of("MENTION", "COMMAND"), Map.of());
            }

            @Override
            public PluginAiToolResult execute(PluginAiExecutionContext executionContext, PluginAiToolCall call) {
                return new PluginAiToolResult("imported", "导入完成", Map.of("count", 1));
            }
        };
    }
}
