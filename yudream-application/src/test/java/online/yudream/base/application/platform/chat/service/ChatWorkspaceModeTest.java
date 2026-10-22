package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatWorkspaceModeTest {

    static class MemoryStore implements ChatWorkspaceStore {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        public List<Entry> list(Long userId) { return files.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue().length, "now")).toList(); }
        public byte[] readFile(Long userId, String path) { return files.get(path); }
        public void writeFile(Long userId, String path, byte[] data) { files.put(path, data); }
        public void delete(Long userId, String path) { files.remove(path); }
    }

    private static AiAgentTool tool(List<AiAgentTool> tools, String name) {
        return tools.stream().filter(t -> t.descriptor().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void editableToolsWriteAndDeleteOwnWorkspaceAndAlwaysNeedConfirmation() {
        MemoryStore store = new MemoryStore();
        List<AiAgentTool> tools = ChatWorkspaceTools.createEditable(store, 7L);
        AiAgentTool write = tool(tools, ChatWorkspaceTools.WRITE_TOOL);
        AiAgentTool delete = tool(tools, ChatWorkspaceTools.DELETE_TOOL);
        assertEquals(AgentToolRisk.WRITE, write.risk());
        assertTrue(write.requiresConfirmation());
        assertTrue(delete.requiresConfirmation());
        assertEquals(ChatWorkspaceTools.EDIT_PERMISSION_CODE, write.descriptor().permissionCode());

        var created = write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "/notes/plan.md", "content", "# 计划")));
        assertEquals(true, created.payload().get("created"));
        assertEquals("# 计划", new String(store.files.get("notes/plan.md"), StandardCharsets.UTF_8));
        var overwritten = write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "notes/plan.md", "content", "v2")));
        assertEquals(false, overwritten.payload().get("created"));

        delete.execute(new AiAgentToolCall(ChatWorkspaceTools.DELETE_TOOL, Map.of("path", "notes/plan.md")));
        assertTrue(store.files.isEmpty());
        assertThrows(BizException.class, () -> delete.execute(new AiAgentToolCall(ChatWorkspaceTools.DELETE_TOOL, Map.of("path", "missing.md"))));
    }

    @Test
    void unsafePathsAndOversizedOrNonTextContentAreRejected() {
        for (String path : List.of("", "../secret", "a/../../b", "dir/", "a\\b", "x".repeat(201), "bad\u0001name")) {
            assertThrows(BizException.class, () -> ChatWorkspaceTools.safePath(path), path);
        }
        assertEquals("ok/file.txt", ChatWorkspaceTools.safePath("  /ok/file.txt "));
        AiAgentTool write = tool(ChatWorkspaceTools.createEditable(new MemoryStore(), 7L), ChatWorkspaceTools.WRITE_TOOL);
        assertThrows(BizException.class, () -> write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "a.txt", "content", 42))));
        assertThrows(BizException.class, () -> write.execute(new AiAgentToolCall(ChatWorkspaceTools.WRITE_TOOL, Map.of("path", "big.txt", "content", "x".repeat(1024 * 1024 + 1)))));
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
        assertTrue(ChatRunAppService.modePrompt("code").contains("workspace.write"));
    }
}
