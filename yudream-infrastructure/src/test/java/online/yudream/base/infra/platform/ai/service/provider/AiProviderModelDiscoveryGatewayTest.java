package online.yudream.base.infra.platform.ai.service.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import online.yudream.base.application.common.net.OutboundNetworkPolicy;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型发现网关：用本地 HTTP 服务验证路径拼接、鉴权头、解析与去重；不访问任何外部网络。
 */
class AiProviderModelDiscoveryGatewayTest {

    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);

    private AiProviderModelDiscoveryGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        gateway = new AiProviderModelDiscoveryGateway(
                new AiProviderConfigParser(),
                new OutboundNetworkPolicy(false)
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath.set(exchange.getRequestURI().getPath());
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
        byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus.get(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Test
    void listsOpenAiCompatibleModelsWithBearerAuth() {
        responseBody.set("""
                {"data":[{"id":"gpt-5"},{"id":"gpt-4o-mini"},{"id":"gpt-5"}]}
                """);

        var result = gateway.discover(new AiModelDiscoveryQuery(
                "openai", "chat", "CHAT", baseUrl(), "sk-live", "", Map.of()));

        assertTrue(result.listed());
        assertEquals("/v1/models", lastPath.get());
        assertEquals("Bearer sk-live", lastAuthorization.get());
        // 去重并保持稳定排序
        assertEquals(List.of("gpt-4o-mini", "gpt-5"), result.models().stream().map(m -> m.code()).toList());
        assertEquals("chat", result.models().get(0).kind());
    }

    @Test
    void listsAnthropicModelsWithApiKeyHeader() {
        responseBody.set("""
                {"data":[{"id":"claude-sonnet","display_name":"Claude Sonnet"}]}
                """);

        var result = gateway.discover(new AiModelDiscoveryQuery(
                "anthropic", "chat", "ANTH", "http://127.0.0.1:" + server.getAddress().getPort(), "sk-anth", "", Map.of()));

        assertTrue(result.listed());
        assertEquals("/v1/models", lastPath.get());
        assertEquals("sk-anth", lastApiKeyHeader.get());
        assertEquals("Claude Sonnet", result.models().get(0).name());
    }

    @Test
    void reusesStoredCredentialsWhenKeyIsBlank() {
        responseBody.set("""
                {"data":[{"id":"text-embedding-3-small"}]}
                """);
        String storedConfig = """
                [{
                  "code": "openai",
                  "usage": "embedding",
                  "baseUrl": "%s",
                  "apiKey": "sk-stored",
                  "models": [{ "code": "text-embedding-3-small", "model": "text-embedding-3-small" }]
                }]
                """.formatted(baseUrl());

        var result = gateway.discover(new AiModelDiscoveryQuery(
                "openai", "embedding", "EMBEDDING", "", "", "", Map.of("providers", storedConfig)));

        assertTrue(result.listed());
        assertEquals("Bearer sk-stored", lastAuthorization.get());
        assertEquals("/v1/models", lastPath.get());
        assertEquals(List.of("text-embedding-3-small"), result.models().stream().map(m -> m.code()).toList());
    }

    @Test
    void reportsUnsupportedForRerankEndpoints() {
        var result = gateway.discover(new AiModelDiscoveryQuery(
                "jina", "rerank", "RERANK", baseUrl(), "sk-jina", "", Map.of()));

        assertFalse(result.listed());
        assertTrue(result.models().isEmpty());
        assertTrue(result.message().contains("手工登记"));
    }

    @Test
    void surfacesHttpFailureWithStatus() {
        responseStatus.set(401);
        responseBody.set("{\"error\":{\"message\":\"invalid api key\"}}");

        BizException exception = assertThrows(BizException.class, () -> gateway.discover(new AiModelDiscoveryQuery(
                "openai", "chat", "CHAT", baseUrl(), "sk-bad", "", Map.of())));
        assertTrue(exception.getMessage().contains("401"));
    }

    @Test
    void rejectsMissingCredentialAndAddress() {
        assertThrows(BizException.class, () -> gateway.discover(new AiModelDiscoveryQuery(
                "openai", "chat", "CHAT", "", "sk", "", Map.of())));
        assertThrows(BizException.class, () -> gateway.discover(new AiModelDiscoveryQuery(
                "openai", "chat", "CHAT", baseUrl(), "", "", Map.of())));
    }
}
