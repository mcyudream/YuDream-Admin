package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.workspace.service.WorkspaceAccess;
import online.yudream.base.application.platform.workspace.service.WorkspaceTestFixture;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatWorkspaceModeTest {

    private static AiAgentTool tool(List<AiAgentTool> tools, String name) {
        return tools.stream().filter(t -> t.descriptor().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void editableToolsWriteMoveAndTrashAndAlwaysNeedConfirmation() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        WorkspaceAccess access = f.service.access(7L, "");
        List<AiAgentTool> tools = ChatWorkspaceTools.createEditable(access);
        AiAgentTool write = tool(tools, ChatWorkspaceTools.WRITE_TOOL);
        AiAgentTool move = tool(tools, ChatWorkspaceTools.MOVE_TOOL);
        AiAgentTool delete = tool(tools, ChatWorkspaceTools.DELETE_TOOL);
        for (AiAgentTool tool : tools) {
            assertEquals(AgentToolRisk.WRITE, tool.risk());
            assertTrue(tool.requiresConfirmation());
            assertEquals(ChatWorkspaceTools.EDIT_PERMISSION_CODE, tool.descriptor().permissionCode());
        }
        var created = write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "/notes/plan.md", "content", "# 计划")));
        assertEquals(true, created.payload().get("created"));
        var overwritten = write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "notes/plan.md", "content", "v2")));
        assertEquals(false, overwritten.payload().get("created"));
        move.execute(new AiAgentToolCall(ChatWorkspaceTools.MOVE_TOOL, Map.of("from", "notes", "to", "archive/notes")));

        List<AiAgentTool> readers = ChatWorkspaceTools.create(access);
        var listed = tool(readers, ChatWorkspaceTools.LIST_TOOL).execute(new AiAgentToolCall(ChatWorkspaceTools.LIST_TOOL, Map.of("directory", "archive")));
        assertEquals(2, listed.payload().get("count"));
        var read = tool(readers, ChatWorkspaceTools.READ_TOOL).execute(new AiAgentToolCall(ChatWorkspaceTools.READ_TOOL, Map.of("path", "archive/notes/plan.md")));
        assertEquals("v2", read.payload().get("content"));

        delete.execute(new AiAgentToolCall(ChatWorkspaceTools.DELETE_TOOL, Map.of("path", "archive")));
        assertEquals(1, f.service.trash(7L).size());
        assertThrows(BizException.class, () -> delete.execute(new AiAgentToolCall(ChatWorkspaceTools.DELETE_TOOL, Map.of("path", "missing.md"))));
    }

    @Test
    void oversizedOrNonTextContentAndUnsafePathsAreRejected() {
        AiAgentTool write = tool(ChatWorkspaceTools.createEditable(new WorkspaceTestFixture().service.access(7L, "")), ChatWorkspaceTools.WRITE_TOOL);
        assertThrows(BizException.class, () -> write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "a.txt", "content", 42))));
        assertThrows(BizException.class, () -> write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "big.txt", "content", "x".repeat(1024 * 1024 + 1)))));
        assertThrows(BizException.class, () -> write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "../escape.txt", "content", "x"))));
        assertEquals(List.of(), ChatWorkspaceTools.create(null));
    }

    @Test
    void modesNormalizeAndOnlyPlanAndCodeAddInstructions() {
        ChatSendCmd cmd = new ChatSendCmd();
        assertEquals("ask", ChatRunAppService.workspaceMode(cmd));
        cmd.setMode(" CODE ");
        assertEquals("code", ChatRunAppService.workspaceMode(cmd));
        cmd.setMode("shell");
        assertEquals("ask", ChatRunAppService.workspaceMode(cmd));
        assertEquals("", ChatRunAppService.modePrompt("ask"));
        assertTrue(ChatRunAppService.modePrompt("plan").contains("不修改任何内容"));
        assertTrue(ChatRunAppService.modePrompt("code").contains("workspace.move"));
    }
}
