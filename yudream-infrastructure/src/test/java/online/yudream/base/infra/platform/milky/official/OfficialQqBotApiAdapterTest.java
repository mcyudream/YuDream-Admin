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
            assertTrue(body.get().contains("\"content\":\"hello\""));
            assertEquals("official-msg", ((Map<?, ?>) result).get("id"));
        } finally {
            server.stop(0);
        }
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
