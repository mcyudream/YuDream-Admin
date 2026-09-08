package online.yudream.base.domain.platform.milky.model;

import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialQqBotCommandPanelTest {

    @Test
    void usesCommandTriggerAsClickableName() {
        List<Map<String, Object>> items = OfficialQqBotCommandPanel.items(List.of(
                command("SYSTEM", "system.menu", "菜单", "菜单"),
                command("sample", "sign", "签到", "每日签到")));

        assertEquals(2, items.size());
        assertEquals("command", items.get(0).get("type"));
        assertEquals("菜单", items.get(0).get("name"));
        assertEquals("/签到", items.get(1).get("name"));
        assertEquals("每日签到", items.get(1).get("desc"));
        assertEquals(false, items.get(1).get("only_admin"));
    }

    @Test
    void capsPanelAtTwentyCommands() {
        List<PluginCommandInfo> commands = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            commands.add(command("p", "c" + index, "cmd" + index, "指令" + index));
        }
        assertEquals(20, OfficialQqBotCommandPanel.items(commands).size());
        assertEquals("/cmd1", OfficialQqBotCommandPanel.items(commands).getFirst().get("name"));
        assertEquals("/cmd20", OfficialQqBotCommandPanel.items(commands).get(19).get("name"));
    }

    @Test
    void createPayloadIsGlobalForNamedScope() {
        Map<String, Object> payload = OfficialQqBotCommandPanel.createPayload("group", List.of(
                command("SYSTEM", "system.menu", "菜单", "菜单")));
        assertEquals("group", payload.get("scope"));
        assertEquals("all", payload.get("target_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> panel = (Map<String, Object>) payload.get("panel");
        assertEquals(OfficialQqBotCommandPanel.REMARK, panel.get("remark"));
    }

    @Test
    void findsManagedPanelAndIgnoresOthers() {
        Map<String, Object> response = Map.of(
                "records", List.of(
                        Map.of("panel_id", "p_other", "panel", Map.of("remark", "manual")),
                        Map.of("panel_id", "p_sys", "panel", Map.of("remark", OfficialQqBotCommandPanel.REMARK))),
                "is_end", false,
                "next_cursor", "abc");
        assertEquals("p_sys", OfficialQqBotCommandPanel.findManagedPanelId(response));
        assertEquals("abc", OfficialQqBotCommandPanel.nextCursor(response));
        assertNull(OfficialQqBotCommandPanel.findManagedPanelId(Map.of("records", List.of(), "is_end", true)));
        assertTrue(OfficialQqBotCommandPanel.listEnded(Map.of("records", List.of(), "is_end", true)));
        assertEquals("p_created", OfficialQqBotCommandPanel.panelId(Map.of("panel_id", "p_created")));
    }

    @Test
    void samePanelComparesRemarkAndItemsOnly() {
        List<PluginCommandInfo> commands = List.of(command("sample", "sign", "签到", "每日签到"));
        Map<String, Object> remote = Map.of(
                "remark", OfficialQqBotCommandPanel.REMARK,
                "items", OfficialQqBotCommandPanel.items(commands),
                "extra", "ignored");
        assertTrue(OfficialQqBotCommandPanel.samePanel(remote, commands));
        assertTrue(!OfficialQqBotCommandPanel.samePanel(remote, List.of(
                command("SYSTEM", "system.menu", "菜单", "菜单"))));
        assertTrue(!OfficialQqBotCommandPanel.samePanel(
                Map.of("remark", "manual", "items", OfficialQqBotCommandPanel.items(commands)), commands));
        Map<String, Object> renamed = Map.of(
                "remark", OfficialQqBotCommandPanel.REMARK,
                "items", List.of(Map.of(
                        "type", "command",
                        "name", "/签到",
                        "desc", "旧名称",
                        "only_admin", false)));
        assertTrue(!OfficialQqBotCommandPanel.samePanel(renamed, commands));
    }

    private PluginCommandInfo command(String pluginCode, String code, String command, String name) {
        return new PluginCommandInfo(pluginCode, code, command, name, null, name, true);
    }
}
