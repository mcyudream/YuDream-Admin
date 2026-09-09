package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialQqBotEventNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();

    @Test
    void mapsGroupAtMessageToMessageReceive() throws Exception {
        String payload = """
                {"op":0,"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"msg-1","group_openid":"group-open","content":"/帮助",
                  "author":{"member_openid":"member-1","username":"alice"},
                  "timestamp":"2026-09-06T00:00:00+08:00"
                }}
                """;
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree(payload), sessions, 9L);
        assertEquals("message_receive", event.eventType());
        assertEquals("group", event.data().get("message_scene"));
        assertEquals("group-open", event.data().get("peer_id"));
        assertEquals("member-1", event.data().get("sender_id"));
        assertEquals("msg-1", event.data().get("message_seq"));
        assertEquals("group-open", sessions.groupList(9L).getFirst().get("group_id"));
        assertEquals("group-open", sessions.groupList(9L).getFirst().get("group_name"));
        assertEquals("alice", sessions.groupMembers(9L, "group-open").getFirst().get("nickname"));
        assertEquals("msg-1", sessions.lastInbound(9L, "group-open").msgId());
        assertEquals("/帮助", sessions.history(9L, "group", "group-open", null, 10).getFirst().get("raw_message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stripsBotMentionFromGroupAtCommandAndKeepsEnvelopeEventId() throws Exception {
        sessions.rememberSelf(9L, "bot-open");
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"id":"evt-9","t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"msg-at","group_openid":"group-open","content":"<@!bot-open> /签到",
                  "author":{"member_openid":"member-1"},
                  "mentions":[{"id":"bot-open","bot":true},{"id":"member-2"}]
                }}
                """), sessions, 9L);
        List<Map<String, Object>> segments = (List<Map<String, Object>>) event.data().get("segments");
        assertEquals("text", segments.getFirst().get("type"));
        assertEquals("/签到", ((Map<?, ?>) segments.getFirst().get("data")).get("text"));
        assertEquals("evt-9", event.data().get("event_id"));
        assertEquals("msg-at", sessions.lastInbound(9L, "group-open").msgId());
        assertEquals("evt-9", sessions.lastInbound(9L, "group-open").eventId());
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertEquals(1, segments.stream().filter(segment -> "mention".equals(segment.get("type"))).count());
        assertTrue(segments.stream().noneMatch(segment ->
                "mention".equals(segment.get("type"))
                        && "bot-open".equals(((Map<?, ?>) segment.get("data")).get("user_id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void marksOfficialAtMessageAsDirectedAtBotWithoutInventingMentionSegments() throws Exception {
        sessions.rememberSelf(9L, "bot-open");
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"msg-chat","group_openid":"group-open","content":"<@!bot-open> 你好",
                  "author":{"member_openid":"member-1"},
                  "mentions":[{"id":"bot-open","bot":true}]
                }}
                """), sessions, 9L);
        List<Map<String, Object>> segments = (List<Map<String, Object>>) event.data().get("segments");
        assertEquals("你好", ((Map<?, ?>) segments.getFirst().get("data")).get("text"));
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertTrue(segments.stream().noneMatch(segment -> "mention".equals(segment.get("type"))));
        assertEquals("bot-open", event.selfId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsGuildAtMessageToChannelSceneWithoutFakingMilkyMentions() throws Exception {
        sessions.rememberSelf(9L, "bot-open");
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"id":"evt-ch","t":"AT_MESSAGE_CREATE","d":{
                  "id":"msg-ch","guild_id":"guild-1","channel_id":"ch-1","content":"<@!bot-open> 你好",
                  "author":{"id":"member-1","username":"alice"}
                }}
                """), sessions, 9L);
        assertEquals("message_receive", event.eventType());
        assertEquals("channel", event.data().get("message_scene"));
        assertEquals("ch-1", event.data().get("peer_id"));
        assertEquals("guild-1", event.data().get("guild_id"));
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertEquals("你好", ((Map<?, ?>) ((List<Map<String, Object>>) event.data().get("segments")).getFirst().get("data")).get("text"));
        assertEquals("guild-1", sessions.guildList(9L).getFirst().get("guild_id"));
    }

    @Test
    void remembersGroupNameFromEventAndDoesNotOverwriteWithBlank() throws Exception {
        OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_ADD_ROBOT","d":{"group_openid":"group-open","group_name":"读书会"}}
                """), sessions, 9L);
        assertEquals("读书会", sessions.groupList(9L).getFirst().get("group_name"));

        OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"msg-2","group_openid":"group-open","content":"hi",
                  "author":{"member_openid":"member-1"}
                }}
                """), sessions, 9L);
        assertEquals("读书会", sessions.groupList(9L).getFirst().get("group_name"));
        assertTrue(!sessions.needsGroupName(9L, "group-open"));
    }

    @Test
    void mapsC2cMessageAndInteraction() throws Exception {
        MilkyModels.Event privateMessage = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"C2C_MESSAGE_CREATE","d":{"id":"p1","openid":"user-open","content":"hi",
                  "author":{"user_openid":"user-open","username":"bob"}}}
                """), sessions, 3L);
        assertEquals("friend", privateMessage.data().get("message_scene"));
        assertEquals("user-open", privateMessage.data().get("peer_id"));
        assertEquals(Boolean.TRUE, privateMessage.data().get("mention_self"));

        MilkyModels.Event click = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"INTERACTION_CREATE","d":{"id":"i1","group_openid":"g1","user_openid":"u1",
                  "data":{"resolved":{"button":{"id":"weather:refresh"}}}}}
                """), sessions, 3L);
        assertEquals("button_click", click.eventType());
        assertEquals("weather:refresh", click.data().get("button_id"));
        assertEquals("i1", click.data().get("interaction_id"));
        assertEquals("group", click.data().get("message_scene"));
        assertEquals("button_click", OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"INTERACTION_CREATE","d":{"id":"i2","group_openid":"g1","user_openid":"u1",
                  "data":{"resolved":{"button":{"id":"weather:refresh"}}}}}
                """), sessions, 3L).eventType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsInteractionTextWithoutButtonIdToDirectedMessage() throws Exception {
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"id":"evt-i","t":"INTERACTION_CREATE","d":{
                  "id":"8dc58cc7-2d0e-4917-b610-66ecd1be93a0",
                  "group_openid":"g1","group_member_openid":"u1","chat_type":1,
                  "data":{"type":11,"resolved":{"button_data":"你是谁"}}
                }}
                """), sessions, 3L);
        assertEquals("message_receive", event.eventType());
        assertEquals("group", event.data().get("message_scene"));
        assertEquals("g1", event.data().get("peer_id"));
        assertEquals("u1", event.data().get("sender_id"));
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertEquals("INTERACTION_CREATE", event.data().get("native_type"));
        assertEquals("8dc58cc7-2d0e-4917-b610-66ecd1be93a0", event.data().get("interaction_id"));
        assertEquals("你是谁", ((Map<?, ?>) ((List<Map<String, Object>>) event.data().get("segments")).getFirst().get("data")).get("text"));
        assertEquals("8dc58cc7-2d0e-4917-b610-66ecd1be93a0", sessions.lastInbound(3L, "g1").msgId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsKeyboardInteractionWithButtonIdToDirectedMessage() throws Exception {
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"INTERACTION_CREATE","d":{
                  "id":"i-kb","group_openid":"g1","group_member_openid":"u1",
                  "data":{"type":11,"resolved":{"button_id":"input-1","button_data":"你是谁"}}
                }}
                """), sessions, 3L);
        assertEquals("message_receive", event.eventType());
        assertEquals("你是谁", ((Map<?, ?>) ((List<Map<String, Object>>) event.data().get("segments")).getFirst().get("data")).get("text"));
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertEquals("input-1", OfficialQqBotEventNormalizer.interactionButtonId(
                mapper.readTree("{\"data\":{\"resolved\":{\"button_id\":\"input-1\"}}}")));
    }

    @Test
    void mapsCommandPanelInteractionNameToDirectedMessage() throws Exception {
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"INTERACTION_CREATE","d":{
                  "id":"i-cmd","user_openid":"u1","scene":"c2c",
                  "data":{"type":"command","name":"/签到"}
                }}
                """), sessions, 3L);
        assertEquals("message_receive", event.eventType());
        assertEquals("friend", event.data().get("message_scene"));
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertEquals("/签到", event.data().get("raw_message"));
    }

    @Test
    void mapsJoinRequestAndNoticeEvents() throws Exception {
        MilkyModels.Event join = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_JOIN_REQUEST","d":{"id":"req-1","group_openid":"g1","op_member_openid":"u1"}}
                """), sessions, 4L);
        assertEquals("group_request", join.eventType());
        assertEquals("g1", join.data().get("group_id"));
        assertEquals("u1", join.data().get("user_id"));
        assertEquals("req-1", join.data().get("request_id"));
        assertEquals("req-1", join.data().get("join_request_id"));

        MilkyModels.Event reject = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_MSG_REJECT","d":{"group_openid":"g1"}}
                """), sessions, 4L);
        assertEquals("message_reject", reject.eventType());
        assertEquals("GROUP_MSG_REJECT", reject.data().get("native_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsOfficialJoinRequestVerifyMessageAndJoinRequestId() throws Exception {
        MilkyModels.Event join = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_JOIN_REQUEST","d":{
                  "group_openid":"g-open",
                  "join_request_id":"jr-42",
                  "member_openid":"member-open",
                  "username":"alice",
                  "apply_source":"self_apply",
                  "verify_info":{"method":"verify_message","verify_message":"allow"}
                }}
                """), sessions, 4L);
        assertEquals("group_request", join.eventType());
        assertEquals("g-open", join.data().get("group_id"));
        assertEquals("member-open", join.data().get("user_id"));
        assertEquals("jr-42", join.data().get("request_id"));
        assertEquals("jr-42", join.data().get("join_request_id"));
        assertEquals("allow", join.data().get("comment"));
        assertEquals("alice", join.data().get("username"));
        Map<String, Object> nativeData = (Map<String, Object>) join.data().get("native");
        assertEquals("jr-42", nativeData.get("join_request_id"));
    }

    @Test
    void mapsOfficialJoinRequestQaListIntoComment() throws Exception {
        MilkyModels.Event join = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_JOIN_REQUEST","d":{
                  "group_openid":"g-open",
                  "join_request_id":"jr-qa",
                  "member_openid":"member-open",
                  "verify_info":{"method":"admin_review_qa","review_qa_list":[
                    {"question":"物品聚合器的作用","answer":"垃圾桶"}
                  ]}
                }}
                """), sessions, 4L);
        assertEquals("物品聚合器的作用：垃圾桶", join.data().get("comment"));
        assertEquals("jr-qa", join.data().get("request_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractsTextAndImageSegments() throws Exception {
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_AT_MESSAGE_CREATE","d":{"id":"m","group_openid":"g","content":"hello",
                  "author":{"member_openid":"u"},
                  "attachments":[{"url":"https://img.example/a.png","content_type":"image/png"}]}}
                """), sessions, 1L);
        List<Map<String, Object>> segments = (List<Map<String, Object>>) event.data().get("segments");
        assertEquals("text", segments.getFirst().get("type"));
        assertEquals("image", segments.get(1).get("type"));
        assertTrue(String.valueOf(((Map<?, ?>) segments.getFirst().get("data")).get("text")).contains("hello"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void treatsDisplayNameAtAsDirectedAtBotAndStripsItFromChatText() throws Exception {
        sessions.rememberSelf(9L, "bot-open", "MC梦璃");
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_MESSAGE_CREATE","d":{
                  "id":"msg-chat","group_openid":"group-open","content":"@MC梦璃 你是谁",
                  "author":{"member_openid":"member-1"}
                }}
                """), sessions, 9L);
        List<Map<String, Object>> segments = (List<Map<String, Object>>) event.data().get("segments");
        assertEquals("你是谁", ((Map<?, ?>) segments.getFirst().get("data")).get("text"));
        assertEquals(Boolean.TRUE, event.data().get("mention_self"));
        assertEquals("GROUP_MESSAGE_CREATE", event.data().get("native_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotTreatMentioningSomeoneElseAsDirectedAtBot() throws Exception {
        sessions.rememberSelf(9L, "bot-open", "MC梦璃");
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_MESSAGE_CREATE","d":{
                  "id":"msg-other","group_openid":"group-open","content":"@张三 吃饭了吗",
                  "author":{"member_openid":"member-1"},
                  "mentions":[{"id":"member-2"}]
                }}
                """), sessions, 9L);
        List<Map<String, Object>> segments = (List<Map<String, Object>>) event.data().get("segments");
        assertEquals("@张三 吃饭了吗", ((Map<?, ?>) segments.getFirst().get("data")).get("text"));
        assertEquals(Boolean.FALSE, event.data().get("mention_self"));
        assertEquals("member-2", ((Map<?, ?>) segments.stream()
                .filter(segment -> "mention".equals(segment.get("type")))
                .findFirst()
                .orElseThrow()
                .get("data")).get("user_id"));
        assertFalse(OfficialQqBotEventNormalizer.looksLikeBotMention("@张三 吃饭了吗", "bot-open", "MC梦璃"));
        assertFalse(OfficialQqBotEventNormalizer.looksLikeBotMention("<@member-2> 吃饭了吗", "bot-open", "MC梦璃"));
        assertTrue(OfficialQqBotEventNormalizer.looksLikeBotMention("<@!bot-open> 你好", "bot-open", "MC梦璃"));
        assertEquals("@张三 吃饭了吗", OfficialQqBotEventNormalizer.stripBotMentions("@张三 吃饭了吗", "bot-open", "MC梦璃"));
    }

    @Test
    void remembersBotDisplayNameFromReadyEvent() throws Exception {
        OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"READY","d":{"user":{"id":"bot-open","username":"MC梦璃"}}}
                """), sessions, 9L);
        assertEquals("bot-open", sessions.selfId(9L));
        assertEquals("MC梦璃", sessions.selfName(9L));
    }

    @Test
    void unknownOfficialEventsKeepNativeTypeForSystemLogs() throws Exception {
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"FORUM_THREAD_CREATE","d":{"guild_id":"g1","thread_id":"t1"}}
                """), sessions, 9L);
        assertEquals("forum_thread_create", event.eventType());
        assertEquals("FORUM_THREAD_CREATE", event.data().get("native_type"));
        assertEquals("g1", event.data().get("guild_id"));
    }
}
