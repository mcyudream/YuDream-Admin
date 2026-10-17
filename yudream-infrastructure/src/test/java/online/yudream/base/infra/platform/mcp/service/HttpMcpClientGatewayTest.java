package online.yudream.base.infra.platform.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import online.yudream.base.application.common.net.OutboundNetworkPolicy;
import online.yudream.base.domain.platform.mcp.service.McpClientGateway;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Streamable HTTP 客户端：初始化握手、协议版本回退、分页工具清单、SSE 响应、
 * 工具调用渲染与服务头回传。
 */
class HttpMcpClientGatewayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final List<String> methods = new ArrayList<>();
    private final List<String> sessionHeaders = new ArrayList<>();
    private final List<String> requestedProtocolVersions = new ArrayList<>();
    private boolean sseResponses;
    /** 非空时只接受该协议版本，其余版本返回 JSON-RPC 错误。 */
    private String requiredProtocolVersion;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void 探测返回分页工具清单并回传会话头() {
        McpClientGateway.McpProbeResult result = gateway().probe(endpoint());

        assertTrue(result.success(), result.message());
        assertEquals("2025-06-18", result.protocolVersion());
        assertEquals("stub-mcp", result.serverName());
        assertEquals(List.of("initialize", "notifications/initialized", "tools/list", "tools/list"), methods);
        assertEquals(2, result.tools().size(), "分页游标应继续拉取");

        McpToolDefinition echo = result.tools().get(0);
        assertEquals("echo", echo.name());
        assertEquals("READ", echo.risk(), "readOnlyHint 推导只读");
        assertEquals("object", echo.inputSchema().get("type"));

        McpToolDefinition danger = result.tools().get(1);
        assertEquals("danger", danger.name());
        assertEquals("WRITE", danger.risk());
        assertTrue(danger.requiresConfirmation(), "destructiveHint 需要确认");

        assertTrue(sessionHeaders.get(0).isBlank(), "初始化请求不带会话标识");
        assertEquals("session-abc", sessionHeaders.get(2), "initialize 返回的会话标识必须在后续请求回传");
    }

    @Test
    void 支持SSE传输的响应体() {
        sseResponses = true;
        McpClientGateway.McpProbeResult result = gateway().probe(endpoint());
        assertTrue(result.success(), result.message());
        assertEquals(2, result.tools().size());
    }

    @Test
    void 协议版本不被接受时回退旧版本() {
        requiredProtocolVersion = "2024-11-05";
        McpClientGateway.McpProbeResult result = gateway().probe(endpoint());
        assertTrue(result.success(), result.message());
        assertEquals("2024-11-05", result.protocolVersion());
        assertEquals(List.of("2025-06-18", "2024-11-05"), requestedProtocolVersions,
                "首次协商失败后回退旧协议版本重试");
    }

    @Test
    void 工具调用渲染文本内容() {
        McpClientGateway.McpToolCallResult result = gateway().callTool(endpoint(), "echo", Map.of("text", "hi"));

        assertTrue(result.success(), result.error());
        assertFalse(result.isError());
        assertEquals("hello hi", result.content());
        assertEquals(Map.of("echoed", "hi"), result.structured());
        assertTrue(methods.contains("tools/call"));
    }

    @Test
    void 远端返回错误时按错误结果报告() {
        McpClientGateway.McpToolCallResult result = gateway().callTool(endpoint(), "missing", Map.of());
        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("not found"), result.error());
    }

    @Test
    void 连接失败以结果报告而不抛异常() {
        McpClientGateway.McpProbeResult result = gateway().probe(new McpClientGateway.McpEndpoint(
                "broken", "http://127.0.0.1:1/mcp", Map.of(), 2000, true));
        assertFalse(result.success());
        assertFalse(result.message() == null || result.message().isBlank());
    }

    private HttpMcpClientGateway gateway() {
        return new HttpMcpClientGateway(HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), new OutboundNetworkPolicy(true));
    }

    private McpClientGateway.McpEndpoint endpoint() {
        return new McpClientGateway.McpEndpoint("stub", baseUrl, Map.of("X-Test", "1"), 5000, true);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode request = MAPPER.readTree(body);
        String method = request.path("method").asText("");
        methods.add(method);
        sessionHeaders.add(exchange.getRequestHeaders().getFirst("Mcp-Session-Id") == null
                ? "" : exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));

        if ("notifications/initialized".equals(method)) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        String response;
        if ("initialize".equals(method)) {
            String requested = request.path("params").path("protocolVersion").asText("");
            requestedProtocolVersions.add(requested);
            if (requiredProtocolVersion != null && !requiredProtocolVersion.equals(requested)) {
                response = error(request, -32602, "Unsupported protocol version: " + requested);
            } else {
                exchange.getResponseHeaders().add("Mcp-Session-Id", "session-abc");
                response = envelope(request, """
                        {"protocolVersion":"%s","serverInfo":{"name":"stub-mcp","version":"9.9"}}"""
                        .formatted(requiredProtocolVersion == null ? "2025-06-18" : requiredProtocolVersion));
            }
        } else if ("tools/list".equals(method)) {
            boolean second = "2".equals(request.path("params").path("cursor").asText(""));
            response = second
                    ? envelope(request, """
                    {"tools":[{"name":"danger","description":"删除","annotations":{"destructiveHint":true}}]}""")
                    : envelope(request, """
                    {"tools":[{"name":"echo","description":"回显","inputSchema":{"type":"object","properties":{"text":{"type":"string"}}},\
                    "annotations":{"readOnlyHint":true}}],"nextCursor":"2"}""");
        } else if ("tools/call".equals(method)) {
            if (!"echo".equals(request.path("params").path("name").asText(""))) {
                response = error(request, -32601, "not found");
            } else {
                String text = request.path("params").path("arguments").path("text").asText("");
                response = envelope(request, """
                        {"content":[{"type":"text","text":"hello %s"}],"structuredContent":{"echoed":"%s"},"isError":false}"""
                        .formatted(text, text));
            }
        } else {
            response = error(request, -32601, "not found");
        }
        write(exchange, response);
    }

    private void write(HttpExchange exchange, String response) throws IOException {
        if (sseResponses) {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(("event: message\ndata: " + response.replace("\n", " ") + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        } else {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private String envelope(JsonNode request, String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id").asText("1")
                + ",\"result\":" + result.replace("\n", "") + "}";
    }

    private String error(JsonNode request, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id").asText("1")
                + ",\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}";
    }
}
