package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("msg-1", sessions.lastInbound(9L, "group-open").msgId());
    }

    @Test
    void mapsC2cMessageAndInteraction() throws Exception {
        MilkyModels.Event privateMessage = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"C2C_MESSAGE_CREATE","d":{"id":"p1","openid":"user-open","content":"hi",
                  "author":{"user_openid":"user-open","username":"bob"}}}
                """), sessions, 3L);
        assertEquals("friend", privateMessage.data().get("message_scene"));
        assertEquals("user-open", privateMessage.data().get("peer_id"));

        MilkyModels.Event click = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"INTERACTION_CREATE","d":{"id":"i1","group_openid":"g1","user_openid":"u1",
                  "data":{"resolved":{"button":{"id":"weather:refresh"}}}}}
                """), sessions, 3L);
        assertEquals("button_click", click.eventType());
        assertEquals("weather:refresh", click.data().get("button_id"));
    }

    @Test
    void mapsJoinRequestAndNoticeEvents() throws Exception {
        MilkyModels.Event join = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_JOIN_REQUEST","d":{"id":"req-1","group_openid":"g1","op_member_openid":"u1"}}
                """), sessions, 4L);
        assertEquals("group_request", join.eventType());
        assertEquals("g1", join.data().get("group_id"));

        MilkyModels.Event reject = OfficialQqBotEventNormalizer.normalize(mapper.readTree("""
                {"op":0,"t":"GROUP_MSG_REJECT","d":{"group_openid":"g1"}}
                """), sessions, 4L);
        assertEquals("message_reject", reject.eventType());
        assertEquals("GROUP_MSG_REJECT", reject.data().get("native_type"));
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
}
