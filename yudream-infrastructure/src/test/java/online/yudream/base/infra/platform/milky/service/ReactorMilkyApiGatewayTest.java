package online.yudream.base.infra.platform.milky.service;

import com.sun.net.httpserver.HttpServer;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorMilkyApiGatewayTest {

    @Test
    void sendsForwardPayloadContainingNestedMessageLists() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/send_group_message", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"retcode\":0,\"data\":{\"message_seq\":42}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();
            Map<String, Object> forwardNode = Map.of(
                    "user_id", "2675448709",
                    "sender_name", "评论用户",
                    "segments", List.of(Map.of("type", "text", "data", Map.of("text", "评论内容")))
            );
            Map<String, Object> payload = Map.of(
                    "group_id", "1064685901",
                    "message", List.of(Map.of("type", "forward", "data", Map.of("messages", List.of(forwardNode))))
            );

            Object result = new ReactorMilkyApiGateway().invoke(
                    new MilkyModels.Context("http://localhost:" + server.getAddress().getPort(), "test-token", null),
                    "send_group_message", payload);

            assertEquals(Map.of("message_seq", 42), result);
            assertTrue(requestBody.get().contains("\"user_id\":2675448709"));
            assertTrue(requestBody.get().contains("\"group_id\":1064685901"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesCauseWithoutLeakingErrorResponseContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/get_group_list", exchange -> {
            byte[] response = "sensitive error response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            server.start();

            BizException exception = assertThrows(BizException.class, () -> new ReactorMilkyApiGateway().invoke(
                    new MilkyModels.Context("http://localhost:" + server.getAddress().getPort(), "test-token", null),
                    "get_group_list", Map.of()));

            assertEquals("Milky 服务请求失败（HTTP 502）", exception.getMessage());
            assertTrue(exception.getCause() != null);
            assertTrue(!exception.getMessage().contains("sensitive error response"));
        } finally {
            server.stop(0);
        }
    }
}
