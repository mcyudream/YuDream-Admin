package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqSandboxRuntimeGatewayImplTest {

    @Test
    void buildsMilkyNativeDataWithTextMentionsReplyAndSenderMetadata() {
        QqSandboxSession session = QqSandboxSession.create("session-1", "ai-chatbot", "1", "9007199254740993",
                "9007199254740995", null, "9007199254740997", "group", QqSandboxRandomMode.FORCE_HIT,
                1_000L, Instant.now());
        QqSandboxMessageCmd message = new QqSandboxMessageCmd("9007199254740999", "测试用户", "你好", true,
                List.of("9007199254741001", "9007199254741003"), "9007199254741005", "client-9007199254741007",
                null, null);

        Map<String, Object> data = QqSandboxRuntimeGatewayImpl.eventData(session, message);

        assertEquals("9007199254740999", data.get("sender_id"));
        assertEquals("测试用户", data.get("sender_name"));
        assertEquals("测试用户", data.get("sender_nickname"));
        assertEquals("client-9007199254741007", data.get("client_message_id"));
        assertEquals("client-9007199254741007", data.get("message_seq"));
        assertEquals("9007199254740997", data.get("group_id"));
        assertEquals("devtools-sandbox:session-1", session.connectionId());
        assertEquals("1", session.policyConnectionId());
        assertEquals(Map.of(
                "sandboxSessionId", "session-1",
                "sandboxRandomMode", "FORCE_HIT",
                "sandboxPolicyConnectionId", "1"), QqSandboxRuntimeGatewayImpl.sandboxReferrer(session));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) data.get("segments");
        assertEquals("text", segments.getFirst().get("type"));
        assertTrue(segments.stream().anyMatch(segment -> mention(segment, session.selfId())));
        assertTrue(segments.stream().anyMatch(segment -> mention(segment, "9007199254741001")));
        assertTrue(segments.stream().anyMatch(segment -> mention(segment, "9007199254741003")));
        assertTrue(segments.stream().anyMatch(segment -> reply(segment, "9007199254741005")));
    }

    @Test
    void buildsGroupRequestAndButtonClickData() {
        QqSandboxSession session = QqSandboxSession.create("session-2", "demo", "1", "10000",
                "10001", null, "20001", "group", QqSandboxRandomMode.REAL,
                1_000L, Instant.now());
        QqSandboxMessageCmd groupRequest = new QqSandboxMessageCmd("10002", null, "求通过", false,
                List.of(), null, "req-1", "group_request", null);
        Map<String, Object> requestData = QqSandboxRuntimeGatewayImpl.eventData(session, groupRequest);
        // 字段名对齐生产 dispatchGroupRequest 的解析键
        assertEquals("20001", requestData.get("group_id"));
        assertEquals("10002", requestData.get("user_id"));
        assertEquals("req-1", requestData.get("request_id"));
        assertEquals("求通过", requestData.get("comment"));

        QqSandboxMessageCmd button = new QqSandboxMessageCmd("10002", null, null, false,
                List.of(), null, null, "button", "wordle:guess");
        Map<String, Object> buttonData = QqSandboxRuntimeGatewayImpl.eventData(session, button);
        assertEquals("wordle:guess", buttonData.get("button_id"));
        assertEquals("10002", buttonData.get("sender_id"));
        assertEquals("20001", buttonData.get("peer_id"));
    }

    private boolean mention(Map<String, Object> segment, String id) {
        return "mention".equals(segment.get("type")) && id.equals(data(segment).get("user_id"));
    }

    private boolean reply(Map<String, Object> segment, String id) {
        return "reply".equals(segment.get("type")) && id.equals(data(segment).get("message_id"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> segment) {
        return (Map<String, Object>) segment.get("data");
    }
}
