package online.yudream.base.application.platform.agent.workflow.handler;

import online.yudream.base.domain.platform.agent.enumerate.AgentWorkspaceScope;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLlmNodeWorkspaceToolsTest {

    private static AiAgentTool tool(String name) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(name, name, name, null, null, null, null, Map.of());
        return new AiAgentTool() {
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            public AiAgentToolResult execute(AiAgentToolCall call) {
                return null;
            }
        };
    }

    private static List<String> names(List<AiAgentTool> tools) {
        return tools.stream().map(tool -> tool.descriptor().name()).toList();
    }

    @Test
    void nodesInheritOnlyCallerWorkspaceToolsAndOnlyWhenToolsAreAllowed() {
        List<AiAgentTool> node = List.of(tool("wiki.search"));
        assertEquals(List.of("wiki.search"), names(AgentLlmNodeHandler.withCallerWorkspaceTools(node, AiToolMode.AUTO)));
        try (var ignored = AiAgentToolExecutionScope.open(List.of(tool("workspace.list"), tool("workspace.read"), tool("ui.navigate"), tool("wiki.search")), 7L)) {
            assertEquals(List.of("wiki.search", "workspace.list", "workspace.read"), names(AgentLlmNodeHandler.withCallerWorkspaceTools(node, AiToolMode.AUTO)));
            assertEquals(List.of(), names(AgentLlmNodeHandler.withCallerWorkspaceTools(List.of(), AiToolMode.NONE)));
        }
    }

    @Test
    void workspaceScopeMapsToApplicationFolder() {
        assertEquals("", AgentWorkspaceScope.of(null).root("report-bot"));
        assertEquals("", AgentWorkspaceScope.of("shared").root("report-bot"));
        assertEquals("apps/report-bot", AgentWorkspaceScope.of("SCOPED").root("report-bot"));
    }
}
