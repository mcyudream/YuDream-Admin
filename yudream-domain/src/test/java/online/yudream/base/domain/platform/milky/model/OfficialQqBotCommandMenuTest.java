package online.yudream.base.domain.platform.milky.model;

import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialQqBotCommandMenuTest {

    @Test
    void prefixesPluginCommandsAndKeepsMenuAliasesUnprefixed() {
        List<Map<String, Object>> items = OfficialQqBotCommandMenu.items(List.of(
                command("SYSTEM", "system.menu", "菜单", "菜单"),
                command("sample", "sign", "签到", "每日签到"),
                command("sample", "wallet", "/钱包", "钱包")));

        assertEquals(3, items.size());
        assertEquals("菜单", items.get(0).get("send_message"));
        assertEquals("/签到", items.get(1).get("send_message"));
        assertEquals("/钱包", items.get(2).get("send_message"));
        assertEquals("每日签到", items.get(1).get("name"));
    }

    @Test
    void overflowsIntoMoreSubmenuAfterTenTopLevelSlots() {
        List<PluginCommandInfo> commands = new ArrayList<>();
        commands.add(command("SYSTEM", "system.menu", "菜单", "菜单"));
        for (int index = 1; index <= 12; index++) {
            commands.add(command("p", "c" + index, "cmd" + index, "指令" + index));
        }

        List<Map<String, Object>> items = OfficialQqBotCommandMenu.items(commands);
        assertEquals(10, items.size());
        Map<String, Object> more = items.get(9);
        assertEquals("menu", more.get("type"));
        assertEquals("更多", more.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subItems = (List<Map<String, Object>>) more.get("sub_menu_items");
        assertEquals(4, subItems.size());
        assertEquals("/cmd9", subItems.get(0).get("send_message"));
        assertEquals("/cmd12", subItems.get(3).get("send_message"));
    }

    @Test
    void clipsChineseNamesToOfficialCharacterBudget() {
        assertEquals("每日签到", OfficialQqBotCommandMenu.clip("每日签到", 10));
        assertEquals("每日签到查", OfficialQqBotCommandMenu.clip("每日签到查询", 10));
        assertEquals("帮助帮助帮助", OfficialQqBotCommandMenu.clip("帮助帮助帮助", 14));
        assertEquals("帮助帮助帮助帮", OfficialQqBotCommandMenu.clip("帮助帮助帮助帮忙", 14));
    }

    @Test
    void payloadWrapsMenuItems() {
        Map<String, Object> payload = OfficialQqBotCommandMenu.payload(List.of(
                command("SYSTEM", "system.menu", "菜单", "菜单")));
        assertTrue(payload.containsKey("menu"));
        @SuppressWarnings("unchecked")
        Map<String, Object> menu = (Map<String, Object>) payload.get("menu");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) menu.get("items");
        assertEquals(1, items.size());
    }

    @Test
    void sameItemsIgnoresOfficialEnvelopeAndComparesCanonicalMenu() {
        List<PluginCommandInfo> commands = List.of(command("SYSTEM", "system.menu", "菜单", "菜单"));
        Map<String, Object> remote = Map.of(
                "menu", Map.of("items", OfficialQqBotCommandMenu.items(commands), "extra", "ignored"));
        assertTrue(OfficialQqBotCommandMenu.sameItems(remote, commands));
        assertTrue(!OfficialQqBotCommandMenu.sameItems(remote, List.of(
                command("sample", "sign", "签到", "每日签到"))));
    }

    private PluginCommandInfo command(String pluginCode, String code, String command, String name) {
        return new PluginCommandInfo(pluginCode, code, command, name, null, name, true);
    }
}
