package online.yudream.base.infra.platform.milky.official;

import com.sun.net.httpserver.HttpServer;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialQqBotApiAdapterTest {

    @Test
    void mapsSharedSendGroupMessageOntoOfficialRest() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/group-open/messages", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":\"official-msg\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            sessions.rememberInbound(8L, "group-open", "inbound-1", "event-1");
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            Object result = adapter.invoke(context(server.getAddress().getPort()), "send_group_message", Map.of(
                    "group_id", "group-open",
                    "message", List.of(Map.of("type", "text", "data", Map.of("text", "hello")))));
            assertEquals("/v2/groups/group-open/messages", path.get());
            assertTrue(body.get().contains("\"msg_id\":\"inbound-1\""));
            assertTrue(body.get().contains("\"event_id\":\"event-1\""));
            assertTrue(body.get().contains("\"content\":\"hello\""));
            assertEquals("official-msg", ((Map<?, ?>) result).get("id"));
            Object history = adapter.invoke(context(server.getAddress().getPort()), "get_history_messages",
                    Map.of("message_scene", "group", "peer_id", "group-open", "limit", 10));
            assertEquals("official-msg", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) history).get("messages")).getFirst()).get("message_seq"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void historyFallsBackToLocalEventStreamWithoutCallingOfficialApi() {
        OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
        sessions.rememberMessage(8L, "group", "group-open", Map.of(
                "message_seq", "local-1", "sender_id", "member-1", "segments", List.of(Map.of("type", "text", "data", Map.of("text", "菜单")))));
        OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
        Object result = adapter.invoke(new MilkyModels.Context("official", "http://localhost:1", null, "app", "secret", false, null, 8L),
                "get_history_messages", Map.of("message_scene", "group", "peer_id", "group-open", "limit", 20));
        assertEquals("local-1", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) result).get("messages")).getFirst()).get("message_seq"));
    }

    @Test
    void specializedOfficialPathGoesThroughRawEntry() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/g1/files", exchange -> {
            method.set(exchange.getRequestMethod());
            byte[] response = "{\"file_uuid\":\"f1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            Object result = adapter.invoke(context(server.getAddress().getPort()), "POST /v2/groups/g1/files",
                    Map.of("file_type", 1, "url", "https://cdn.example/a.png"));
            assertEquals("POST", method.get());
            assertEquals("f1", ((Map<?, ?>) result).get("file_uuid"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loginInfoUsesUsersMe() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/users/@me", exchange -> {
            byte[] response = "{\"id\":\"bot-open\",\"username\":\"official-bot\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            Object result = adapter.invoke(context(server.getAddress().getPort()), "get_login_info", Map.of());
            assertEquals("bot-open", ((Map<?, ?>) result).get("user_id"));
            assertEquals("bot-open", sessions.selfId(8L));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsOfficialMenuPutOntoRest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/menu", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            byte[] response = "{\"version\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            Object result = adapter.invoke(context(server.getAddress().getPort()), "set_official_menu",
                    Map.of("menu", Map.of("items", List.of(Map.of("type", "send_message", "name", "菜单", "send_message", "菜单")))));
            assertEquals("PUT", method.get());
            assertEquals("/v2/menu", path.get());
            assertEquals(2, ((Map<?, ?>) result).get("version"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsOfficialPanelCreateAndQueryOntoRest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/panels", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getQuery());
            byte[] response = "GET".equals(exchange.getRequestMethod())
                    ? "{\"records\":[],\"is_end\":true}".getBytes(StandardCharsets.UTF_8)
                    : "{\"panel_id\":\"p_group\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            Object created = adapter.invoke(context(server.getAddress().getPort()), "create_official_panel",
                    Map.of("scope", "group", "target_type", "all", "panel", Map.of("items", List.of())));
            assertEquals("POST", method.get());
            assertEquals("/v2/panels", path.get());
            assertEquals("p_group", ((Map<?, ?>) created).get("panel_id"));

            adapter.invoke(context(server.getAddress().getPort()), "get_official_panels",
                    Map.of("scope", "group", "limit", 50));
            assertEquals("GET", method.get());
            assertEquals("scope=group&limit=50", query.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsOfficialPanelUpdateOntoRest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/panels/p_sys", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            byte[] response = "{\"version\":3}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            Object result = adapter.invoke(context(server.getAddress().getPort()), "set_official_panel",
                    Map.of("panel_id", "p_sys", "panel", Map.of("remark", "yudream-system-commands")));
            assertEquals("PUT", method.get());
            assertEquals("/v2/panels/p_sys", path.get());
            assertEquals(3, ((Map<?, ?>) result).get("version"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsGroupInfoOntoOfficialRest() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/group-open/info", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            byte[] response = "{\"group_openid\":\"group-open\",\"group_name\":\"读书会\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            Object result = adapter.invoke(context(server.getAddress().getPort()), "get_group_info", Map.of("group_id", "group-open"));
            assertEquals("/v2/groups/group-open/info", path.get());
            assertEquals("读书会", ((Map<?, ?>) result).get("group_name"));
            assertEquals("读书会", sessions.groupList(8L).getFirst().get("group_name"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fillsMissingGroupNamesWhenListingGroups() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/group-open/info", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            byte[] response = "{\"group_openid\":\"group-open\",\"group_name\":\"读书会\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            sessions.rememberGroup(8L, "group-open", null);
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            Object result = adapter.invoke(context(server.getAddress().getPort()), "get_group_list", Map.of());
            assertEquals("/v2/groups/group-open/info", path.get());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) ((Map<?, ?>) result).get("groups");
            assertEquals("读书会", groups.getFirst().get("group_name"));
            assertEquals("读书会", sessions.groupList(8L).getFirst().get("group_name"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void uploadsBase64ImageBeforeSendingRichMedia() throws Exception {
        AtomicReference<String> uploadBody = new AtomicReference<>();
        AtomicReference<String> sendBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/group-open/files", exchange -> {
            uploadBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"file_info\":\"uploaded-file\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/v2/groups/group-open/messages", exchange -> {
            sendBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":\"img-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            sessions.rememberInbound(8L, "group-open", "inbound-1", "event-1");
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            adapter.invoke(context(server.getAddress().getPort()), "send_group_message", Map.of(
                    "group_id", "group-open",
                    "message", List.of(Map.of("type", "image", "data", Map.of("uri", "base64://aW1hZ2U=")))));
            assertTrue(uploadBody.get().contains("\"file_data\":\"aW1hZ2U=\""));
            assertTrue(uploadBody.get().contains("\"srv_send_msg\":false"));
            assertTrue(sendBody.get().contains("\"file_info\":\"uploaded-file\""));
            assertTrue(sendBody.get().contains("\"msg_type\":7"));
            assertTrue(!sendBody.get().contains("base64://"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stripsInlineBase64CaptionFromOfficialRichMedia() throws Exception {
        AtomicReference<String> sendBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/group-open/files", exchange -> {
            byte[] response = "{\"file_info\":\"uploaded-file\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/v2/groups/group-open/messages", exchange -> {
            sendBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":\"img-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            sessions.rememberInbound(8L, "group-open", "inbound-1", "event-1");
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            adapter.invoke(context(server.getAddress().getPort()), "send_group_message", Map.of(
                    "group_id", "group-open",
                    "msg_type", 7,
                    "content", "说明文字\n换行",
                    "media", Map.of("file_type", 1, "url", "base64://aW1hZ2U=")));
            assertTrue(sendBody.get().contains("\"msg_type\":7"));
            assertTrue(sendBody.get().contains("\"content\":\"\\u200B\"")
                    || sendBody.get().contains("\"content\":\"\u200B\""));
            assertTrue(!sendBody.get().contains("base64://"));
            assertTrue(!sendBody.get().contains("说明文字"));
            assertTrue(!sendBody.get().contains("\\n"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsSharedGroupBanDurationOntoOfficialMemberMute() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/group-open/restrict_chat_setting", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            adapter.invoke(context(server.getAddress().getPort()), "set_group_ban", Map.of(
                    "group_id", "group-open",
                    "user_id", "member-open",
                    "duration", 120));
            assertEquals("POST", method.get());
            assertEquals("/v2/groups/group-open/restrict_chat_setting", path.get());
            assertTrue(body.get().contains("\"op\":\"add\""));
            assertTrue(body.get().contains("\"member_openid\":\"member-open\""));
            assertTrue(body.get().contains("mute_expire_at"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsOfficialChannelSendAndGuildMuteOntoRest() throws Exception {
        AtomicReference<String> sendPath = new AtomicReference<>();
        AtomicReference<String> mutePath = new AtomicReference<>();
        AtomicReference<String> muteMethod = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/channels/ch-1/messages", exchange -> {
            sendPath.set(exchange.getRequestURI().getPath());
            byte[] response = "{\"id\":\"ch-msg\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/guilds/g-1/members/u-1/mute", exchange -> {
            muteMethod.set(exchange.getRequestMethod());
            mutePath.set(exchange.getRequestURI().getPath());
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
            sessions.rememberInbound(8L, "ch-1", "inbound-ch", "event-ch");
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
            Object sent = adapter.invoke(context(server.getAddress().getPort()), "send_channel_message", Map.of(
                    "channel_id", "ch-1",
                    "message", "hello channel"));
            assertEquals("/channels/ch-1/messages", sendPath.get());
            assertEquals("ch-msg", ((Map<?, ?>) sent).get("id"));
            adapter.invoke(context(server.getAddress().getPort()), "set_guild_member_mute", Map.of(
                    "guild_id", "g-1",
                    "user_id", "u-1",
                    "duration", 60));
            assertEquals("PATCH", muteMethod.get());
            assertEquals("/guilds/g-1/members/u-1/mute", mutePath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToCachedMembersWhenOfficialMemberListLacksPermission() {
        OfficialQqBotSessionStore sessions = new OfficialQqBotSessionStore();
        sessions.rememberGroupMember(8L, "group-open", "member-open", "alice");
        OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), sessions);
        Object result = adapter.invoke(new MilkyModels.Context("official", "http://localhost:1", null, "app", "secret", false, null, 8L),
                "get_group_member_list", Map.of("group_id", "group-open"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) ((Map<?, ?>) result).get("members");
        assertEquals("alice", members.getFirst().get("nickname"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) result).get("cached"));
    }

    @Test
    void mapsResourceTempUrlOntoExistingHttpUrlWithoutCallingOfficialApi() {
        OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
        Object result = adapter.invoke(new MilkyModels.Context("official", "http://localhost:1", null, "app", "secret", false, null, 8L),
                "get_resource_temp_url", Map.of("url", "https://img.example/a.png"));
        assertEquals("https://img.example/a.png", ((Map<?, ?>) result).get("url"));
    }

    @Test
    void mapsAckOfficialInteractionOntoPutInteractions() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/interactions/i-1", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            adapter.invoke(context(server.getAddress().getPort()), "ack_official_interaction",
                    Map.of("interaction_id", "i-1"));
            assertEquals("PUT", method.get());
            assertEquals("/interactions/i-1", path.get());
            assertTrue(body.get().contains("\"code\":0"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsApproveJoinBooleanOntoOfficialOpAndJoinRequestId() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/g-open/approval_join_request/member-open", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            adapter.invoke(context(server.getAddress().getPort()), "set_group_add_request", Map.of(
                    "group_id", "g-open",
                    "user_id", "member-open",
                    "approve", true,
                    "request_id", "jr-42"));
            assertEquals("/v2/groups/g-open/approval_join_request/member-open", path.get());
            assertTrue(body.get().contains("\"op\":\"approve\""));
            assertTrue(body.get().contains("\"join_request_id\":\"jr-42\""));
            assertTrue(!body.get().contains("\"approve\":"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsDeclineJoinWithRejectReason() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/groups/g-open/approval_join_request/member-open", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            OfficialQqBotApiAdapter adapter = new OfficialQqBotApiAdapter(fixedTokenClient(), new OfficialQqBotSessionStore());
            adapter.invoke(context(server.getAddress().getPort()), "set_group_add_request", Map.of(
                    "group_id", "g-open",
                    "user_id", "member-open",
                    "approve", false,
                    "join_request_id", "jr-43",
                    "reject_reason", "入群验证未通过"));
            assertTrue(body.get().contains("\"op\":\"decline\""));
            assertTrue(body.get().contains("\"join_request_id\":\"jr-43\""));
            assertTrue(body.get().contains("\"reject_reason\":\"入群验证未通过\""));
        } finally {
            server.stop(0);
        }
    }

    private OfficialQqBotAccessTokenClient fixedTokenClient() {
        return new OfficialQqBotAccessTokenClient() {
            @Override
            public String authorization(MilkyModels.Context context) {
                return "QQBot test-token";
            }
        };
    }

    private MilkyModels.Context context(int port) {
        return new MilkyModels.Context("official", "http://localhost:" + port, null, "app", "secret", false, null, 8L);
    }
}
