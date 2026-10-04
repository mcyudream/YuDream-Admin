package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.valobj.AgentToolPermissions;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolAdapterTest {

    @Test
    void mapsDescriptorToToolBaseMetadata() {
        AiAgentTool tool = tool("cms.canvas.patch", "画布补丁", AgentToolRisk.WRITE, "platform:ai:tool:cms-canvas-patch");
        AgentToolAdapter adapter = new AgentToolAdapter(tool, granted -> true, null);
        assertEquals("cms_canvas_patch", adapter.getName());
        assertTrue(adapter instanceof ToolBase);
        assertFalse(adapter.isReadOnly());
        assertEquals(Map.of("type", "object", "additionalProperties", true,
                "properties", Map.of("patch", Map.of("type", "string", "description", "补丁内容"))),
                adapter.getParameters());
    }

    @Test
    void readOnlyForReadRiskTools() {
        AiAgentTool tool = tool("wiki.search", "知识检索", AgentToolRisk.READ, null);
        AgentToolAdapter adapter = new AgentToolAdapter(tool, granted -> true, null);
        assertTrue(adapter.isReadOnly());
        assertTrue(adapter.isConcurrencySafe());
    }

    @Test
    void deniesWhenPermissionSnapshotMissing() {
        AiAgentTool tool = tool("cms.canvas.patch", "画布补丁", AgentToolRisk.WRITE, "platform:ai:tool:cms");
        AtomicInteger checked = new AtomicInteger();
        AgentToolAdapter adapter = new AgentToolAdapter(tool, new AgentPermissionGateway() {
            @Override
            public boolean hasPermission(String permissionCode) {
                return false;
            }

            @Override
            public boolean hasPermission(String permissionCode, List<String> permissionCodes, boolean explicitContext) {
                checked.incrementAndGet();
                return false;
            }
        }, null);

        var result = adapter.callAsync(param(null)).block();
        assertEquals(ToolResultState.DENIED, result.getState());
        assertEquals(1, checked.get());
    }

    @Test
    void executesAndReturnsJsonWhenPermissionGranted() {
        AiAgentTool tool = tool("wiki.search", "知识检索", AgentToolRisk.READ, "platform:wiki:search");
        AgentToolAdapter adapter = new AgentToolAdapter(tool, new AgentPermissionGateway() {
            @Override
            public boolean hasPermission(String permissionCode) {
                return false;
            }

            @Override
            public boolean hasPermission(String permissionCode, List<String> permissionCodes, boolean explicitContext) {
                return permissionCodes.contains(permissionCode);
            }
        }, null);

        RuntimeContext context = RuntimeContext.builder()
                .put(AgentToolPermissions.class, AgentToolPermissions.explicit(List.of("platform:wiki:search")))
                .build();
        var result = adapter.callAsync(param(context, Map.of("query", "示例"))).block();
        assertEquals(ToolResultState.SUCCESS, result.getState());
        assertTrue(result.getOutput().stream().anyMatch(block ->
                block instanceof io.agentscope.core.message.TextBlock text
                        && text.getText().contains("\"toolName\":\"wiki.search\"")));
    }

    @Test
    void guardRejectionBecomesDeniedResult() {
        AiAgentTool tool = tool("wiki.search", "知识检索", AgentToolRisk.READ, null);
        AgentToolAdapter adapter = new AgentToolAdapter(tool, granted -> true,
                (name, risk, permission) -> {
                    throw new BizException("沙箱内仅允许只读工具");
                });

        var result = adapter.callAsync(param(RuntimeContext.empty())).block();
        assertEquals(ToolResultState.DENIED, result.getState());
    }

    @Test
    void executionErrorBecomesErrorResult() {
        AiAgentTool tool = new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptorOf("boom", null);
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                throw new IllegalStateException("下游不可用");
            }
        };
        AgentToolAdapter adapter = new AgentToolAdapter(tool, granted -> true, null);
        var result = adapter.callAsync(param(RuntimeContext.empty())).block();
        assertEquals(ToolResultState.ERROR, result.getState());
    }

    private ToolCallParam param(RuntimeContext context) {
        return param(context, Map.of());
    }

    private ToolCallParam param(RuntimeContext context, Map<String, Object> input) {
        return ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-1")
                        .name("tool")
                        .input(input)
                        .build())
                .input(input)
                .runtimeContext(context)
                .build();
    }

    private AiAgentTool tool(String name, String title, AgentToolRisk risk, String permissionCode) {
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptorOf(name, title, permissionCode,
                        Map.of("patch", "补丁内容"));
            }

            @Override
            public AgentToolRisk risk() {
                return risk;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                return new AiAgentToolResult(name, "applied", permissionCode, "已应用", Map.of("ok", true));
            }
        };
    }

    private AiAgentToolDescriptor descriptorOf(String name, String title, String permissionCode, Map<String, Object> schema) {
        return new AiAgentToolDescriptor(
                name, title, name + " 描述", permissionCode, title, "平台工具", "测试工具",
                schema, null);
    }

    private AiAgentToolDescriptor descriptorOf(String name, String permissionCode) {
        return descriptorOf(name, name, permissionCode, Map.of());
    }
}
