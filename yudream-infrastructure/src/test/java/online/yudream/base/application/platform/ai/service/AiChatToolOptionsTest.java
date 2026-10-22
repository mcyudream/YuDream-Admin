package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.ai.dto.ChatToolOptionDTO;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 对话中可勾选的工具模块：只含可脱离页面/插件上下文的模块，按权限与开关过滤。 */
class AiChatToolOptionsTest {

    private final ToolTestDoubles.StubCapabilityAppService capability = new ToolTestDoubles.StubCapabilityAppService();
    private final ToolTestDoubles.MemoryToolSettingsRepo settingsRepo = new ToolTestDoubles.MemoryToolSettingsRepo();

    private AiToolModuleAppService service() {
        ToolTestDoubles.MemoryMcpServerRepo mcpRepo = new ToolTestDoubles.MemoryMcpServerRepo();
        mcpRepo.save(ToolTestDoubles.mcpServer("stub", "本地桩", true, false, "panel_overview"));
        List<AiAgentTool> system = List.of(
                new ToolTestDoubles.RecordingAiTool("web.fetch", "网页抓取", "platform:ai:tool:web-fetch", AgentToolRisk.READ, "web"),
                new ToolTestDoubles.RecordingAiTool("wiki.search", "知识库检索", "platform:wiki:view", AgentToolRisk.READ, "wiki"),
                new ToolTestDoubles.RecordingAiTool("cms.canvas.patch", "画布补丁", "", AgentToolRisk.WRITE, "cms"),
                new ToolTestDoubles.RecordingAiTool("ui.navigate", "打开页面", "", AgentToolRisk.READ, "ui"),
                new ToolTestDoubles.RecordingAiTool("workspace.list", "列出工作区", "", AgentToolRisk.READ, "workspace"));
        List<AiAgentTool> mcp = List.of(new ToolTestDoubles.RecordingAiTool("mcp.stub.panel_overview", "面板概览", "platform:mcp:server:use", AgentToolRisk.READ, "mcp:stub"));
        return ToolTestDoubles.moduleService(system, mcpRepo, new ToolTestDoubles.StubAgentToolRepo(), settingsRepo, capability, List.of(),
                new ToolTestDoubles.StubMcpProvider(mcp));
    }

    @Test
    void 只列出有权限且可独立使用的模块() {
        List<ChatToolOptionDTO> options = service().chatToolOptions(List.of("platform:ai:tool:web-fetch", "platform:mcp:server:use"));
        assertEquals(List.of("web", "mcp:stub"), options.stream().map(ChatToolOptionDTO::code).toList());
        assertEquals("mcp", options.get(1).source());
        assertEquals(Set.of("web", "wiki", "mcp:stub"), Set.copyOf(service().chatToolOptions(List.of("*")).stream().map(ChatToolOptionDTO::code).toList()));
    }

    @Test
    void 只装配所选模块中有权限的工具且受开关与能力约束() {
        AiToolModuleAppService service = service();
        List<String> granted = List.of("*");
        assertEquals(List.of("web.fetch"), service.chatModuleTools(List.of("web", "cms", "ui"), granted).stream().map(tool -> tool.descriptor().name()).toList());
        assertTrue(service.chatModuleTools(List.of("wiki"), List.of("platform:ai:tool:web-fetch")).isEmpty());
        service.updateModuleEnabled("web", false);
        assertTrue(service.chatModuleTools(List.of("web"), granted).isEmpty());
        capability.deny = "agent";
        assertTrue(service.chatToolOptions(granted).isEmpty());
        assertTrue(service.chatModuleTools(List.of("wiki"), granted).isEmpty());
    }
}
