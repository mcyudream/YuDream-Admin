package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkyPluginEventDispatcherTest {

    @Test
    void normalizesNullEventDataToAnEmptyMap() {
        assertEquals(Map.of(), MilkyPluginEventDispatcher.eventData(
                new online.yudream.base.domain.platform.milky.model.MilkyModels.Event(1L, "self", "message", null)));
    }

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
    void officialGroupOpenidIsTheChannelId() {
        online.yudream.base.domain.platform.milky.aggregate.MilkyConnection official =
                online.yudream.base.domain.platform.milky.aggregate.MilkyConnection.create(
                        "官方", "official", null, null, "app-1", "secret", false, null, "base64", null);
        assertEquals("group-open", MilkyPluginEventDispatcher.officialGroupOpenid(official, "group", "group-open"));
        assertNull(MilkyPluginEventDispatcher.officialGroupOpenid(official, "friend", "user-open"));
        online.yudream.base.domain.platform.milky.aggregate.MilkyConnection milky =
                online.yudream.base.domain.platform.milky.aggregate.MilkyConnection.create(
                        "本地", "http://127.0.0.1:3010", "token", "base64", null);
        assertNull(MilkyPluginEventDispatcher.officialGroupOpenid(milky, "group", "1064685901"));
    }

    @Test
    void acceptsBothMilkyMessageEventNames() {
        assertTrue(MilkyPluginEventDispatcher.isMessageEvent("message_receive"));
        assertTrue(MilkyPluginEventDispatcher.isMessageEvent("message"));
    }

    @Test
    void normalizesMissingEventDataToAnEmptyMap() {
        assertEquals(Map.of(), MilkyPluginEventDispatcher.eventData(null));
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

    @Test
    void stripsOfficialBotMentionsBeforeParsingCommands() {
        assertEquals("菜单", MilkyPluginEventDispatcher.parseCommand("<@!bot-open> 菜单").name());
        assertEquals("签到", MilkyPluginEventDispatcher.parseCommand("<@123> /签到 extra").name());
        assertEquals(List.of("extra"), MilkyPluginEventDispatcher.parseCommand("<@123> /签到 extra").arguments());
        assertNull(MilkyPluginEventDispatcher.parseCommand("<@!bot-open> hello"));
        assertNull(MilkyPluginEventDispatcher.parseCommand("[图片] 菜单"));
        assertNull(MilkyPluginEventDispatcher.parseCommand("@MC梦璃 你是谁"));
        assertEquals("我的画像", MilkyPluginEventDispatcher.parseCommand("@MC梦璃 /我的画像").name());
    }

    @Test
    void officialDirectedChatKeepsNativeMentionSelfWithoutFakingMentionSegments() {
        Map<String, Object> data = Map.of(
                "native_type", "GROUP_AT_MESSAGE_CREATE",
                "mention_self", true,
                "segments", List.of(
                        Map.of("type", "text", "data", Map.of("text", "你好")),
                        Map.of("type", "mention", "data", Map.of("user_id", "member-2"))
                )
        );

        assertNull(MilkyPluginEventDispatcher.parseCommand("你好"));
        assertEquals(List.of("member-2"), MilkyPluginEventDispatcher.mentionsFromSegments(data.get("segments")));
        assertTrue(MilkyPluginEventDispatcher.officialDirectedAtBot(data));
        assertTrue(MilkyPluginEventDispatcher.officialDirectedAtBot(Map.of(
                "native_type", "INTERACTION_CREATE",
                "mention_self", true
        )));
        assertTrue(!MilkyPluginEventDispatcher.officialDirectedAtBot(Map.of(
                "native_type", "GROUP_MESSAGE_CREATE",
                "mention_self", false
        )));
        assertTrue(MilkyPluginEventDispatcher.officialDirectedAtBot(Map.of(
                "native_type", "GROUP_MESSAGE_CREATE",
                "mention_self", true
        )));
        assertTrue(!MilkyPluginEventDispatcher.officialDirectedAtBot(Map.of(
                "native_type", "INTERACTION_CREATE"
        )));
        Map<String, Object> referrer = new java.util.LinkedHashMap<>();
        MilkyPluginEventDispatcher.copyOfficialReplyIds(Map.of(
                "message_scene", "group",
                "msg_id", "msg-1",
                "event_id", "evt-1",
                "interaction_id", "i-1"
        ), referrer);
        assertEquals("group", referrer.get("message_scene"));
        assertEquals("msg-1", referrer.get("msg_id"));
        assertEquals("i-1", referrer.get("interaction_id"));
    }

    @Test
    void preservesFailureCauseForMenuFallbackHandling() {
        IllegalStateException expected = new IllegalStateException("render failed");

        CompletionException error = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                () -> MilkyPluginEventDispatcher.failedStage(expected).toCompletableFuture().join());

        assertEquals(expected, error.getCause());
    }

    @Test
    void keepsCompletedMenuImageResultWithinDeadline() throws Exception {
        String value = MilkyPluginEventDispatcher.withMenuDeadline(CompletableFuture.completedFuture("sent"))
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals("sent", value);
    }

    @Test
    void failsIncompleteMenuImageStageAtDeadline() {
        CompletableFuture<String> pending = new CompletableFuture<>();

        CompletionException error = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                () -> MilkyPluginEventDispatcher.withMenuDeadline(pending, 1, TimeUnit.MILLISECONDS)
                        .toCompletableFuture().join());

        assertTrue(error.getCause() instanceof java.util.concurrent.TimeoutException);
    }
}
