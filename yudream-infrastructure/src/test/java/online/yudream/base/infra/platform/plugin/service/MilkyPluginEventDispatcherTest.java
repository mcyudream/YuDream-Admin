package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkyPluginEventDispatcherTest {

    @Test
    void normalizesGroupMessageFieldsForCommandDispatch() {
        Map<String, Object> data = Map.of(
                "group_id", 1064685901L,
                "user_id", 3816679582L,
                "message", List.of(Map.of("type", "text", "data", Map.of("text", "/菜单")))
        );

        assertEquals("3816679582", MilkyPluginEventDispatcher.messageUserId(data));
        assertEquals("1064685901", MilkyPluginEventDispatcher.messageChannelId(data));
        assertEquals("/菜单", MilkyPluginEventDispatcher.messageContent(data));
    }

    @Test
    void acceptsBothMilkyMessageEventNames() {
        assertTrue(MilkyPluginEventDispatcher.isMessageEvent("message_receive"));
        assertTrue(MilkyPluginEventDispatcher.isMessageEvent("message"));
    }

    @Test
    void normalizesMilkyJoinNotificationFieldsForPlugins() {
        MilkyPluginEventDispatcher.GroupRequest request = MilkyPluginEventDispatcher.groupRequest(Map.of(
                "group_id", 1064685901L,
                "initiator_id", 3816679582L,
                "notification_seq", 1784279528159539L,
                "comment", "问题：物品聚合器的作用\n答案：垃圾桶"));

        assertNotNull(request);
        assertEquals("1064685901", request.groupId());
        assertEquals("3816679582", request.userId());
        assertEquals("1784279528159539", request.requestId());
        assertEquals("问题：物品聚合器的作用\n答案：垃圾桶", request.comment());
    }

    @Test
    void recognizesUnprefixedMenuAliasesWithSurroundingWhitespace() {
        for (String alias : List.of("菜单", "帮助", "菜单指令")) {
            MilkyPluginEventDispatcher.Parsed command = MilkyPluginEventDispatcher.parseCommand(" \t" + alias + "\n");

            assertNotNull(command);
            assertEquals(alias, command.name());
            assertTrue(command.arguments().isEmpty());
            assertTrue(MilkyPluginEventDispatcher.isMenuAlias(command.name()));
        }
    }

    @Test
    void recognizesMenuAliasesWithCommandPrefixes() {
        for (String alias : List.of("菜单", "帮助", "菜单指令")) {
            for (String prefix : List.of("/", "!")) {
                MilkyPluginEventDispatcher.Parsed command = MilkyPluginEventDispatcher.parseCommand(prefix + alias);

                assertNotNull(command);
                assertEquals(alias, command.name());
                assertTrue(MilkyPluginEventDispatcher.isMenuAlias(command.name()));
            }
        }
    }

    @Test
    void requiresPrefixesForRegularPluginCommands() {
        assertNull(MilkyPluginEventDispatcher.parseCommand("weather beijing"));
        assertNull(MilkyPluginEventDispatcher.parseCommand("菜单 extra"));

        MilkyPluginEventDispatcher.Parsed command = MilkyPluginEventDispatcher.parseCommand("!weather beijing");
        assertNotNull(command);
        assertEquals("weather", command.name());
        assertEquals(List.of("beijing"), command.arguments());
    }
}
