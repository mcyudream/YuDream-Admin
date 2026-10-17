package online.yudream.base.infra.platform.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.common.net.OutboundNetworkPolicy;
import online.yudream.base.application.common.net.OutboundUrlGuard;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcp.service.McpClientGateway;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * MCP Streamable HTTP 客户端：每次操作执行一次
 * 「initialize → notifications/initialized → 业务请求」序列，不持有长连接。
 * <p>
 * 请求统一 POST JSON-RPC；响应既支持 application/json 也支持 text/event-stream
 * （按 SSE 事件解析出匹配 id 的 JSON-RPC 消息）。出站前经 {@link OutboundUrlGuard} 校验，
 * MCP 地址由管理员登记，因此允许按服务记录显式开启私网/回环（自建服务端常见）。
 */
@Slf4j
@Service
public class HttpMcpClientGateway implements McpClientGateway {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String FALLBACK_PROTOCOL_VERSION = "2024-11-05";
    private static final String CLIENT_NAME = "yudream-admin";
    private static final String CLIENT_VERSION = "1.0.0";
    private static final int MAX_TOOL_PAGES = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboundNetworkPolicy outboundNetworkPolicy;
    private final ExecutorService bodyReaders = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "mcp-body-reader");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public HttpMcpClientGateway(OutboundNetworkPolicy outboundNetworkPolicy) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), outboundNetworkPolicy);
    }

    public HttpMcpClientGateway(HttpClient httpClient, OutboundNetworkPolicy outboundNetworkPolicy) {
        this.httpClient = httpClient;
        this.outboundNetworkPolicy = outboundNetworkPolicy;
    }

    @Override
    public McpProbeResult probe(McpEndpoint endpoint) {
        try (Session session = open(endpoint)) {
            List<McpToolDefinition> tools = session.listTools();
            return new McpProbeResult(true, session.protocolVersion, session.serverName, session.serverVersion,
                    tools, "连接成功，发现 " + tools.size() + " 个工具。");
        } catch (BizException exception) {
            return new McpProbeResult(false, null, null, null, List.of(), exception.getMessage());
        } catch (Exception exception) {
            log.debug("MCP probe failed: server={}", endpoint == null ? null : endpoint.code(), exception);
            return new McpProbeResult(false, null, null, null, List.of(), "连接失败：" + exception.getMessage());
        }
    }

    @Override
    public McpToolCallResult callTool(McpEndpoint endpoint, String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank()) {
            return new McpToolCallResult(false, true, null, Map.of(), "工具名为空");
        }
        try (Session session = open(endpoint)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName.trim());
            params.put("arguments", arguments == null ? Map.of() : arguments);
            JsonNode result = session.request("tools/call", params);
            return renderToolResult(result);
        } catch (BizException exception) {
            return new McpToolCallResult(false, true, null, Map.of(), exception.getMessage());
        } catch (Exception exception) {
            log.debug("MCP tool call failed: server={}, tool={}", endpoint == null ? null : endpoint.code(), toolName, exception);
            return new McpToolCallResult(false, true, null, Map.of(), "调用失败：" + exception.getMessage());
        }
    }

    private McpToolCallResult renderToolResult(JsonNode result) {
        if (result == null || result.isNull()) {
            return new McpToolCallResult(true, false, "", Map.of(), null);
        }
        boolean isError = result.path("isError").asBoolean(false);
        StringBuilder text = new StringBuilder();
        for (JsonNode block : result.path("content")) {
            String type = block.path("type").asText("");
            switch (type) {
                case "text" -> text.append(block.path("text").asText("")).append('\n');
                case "image" -> text.append("[图片内容，实际调用不返回二进制]").append('\n');
                case "audio" -> text.append("[音频内容，实际调用不返回二进制]").append('\n');
                case "resource" -> {
                    JsonNode resource = block.path("resource");
                    text.append(resource.path("text").asText(resource.path("uri").asText("[资源内容]"))).append('\n');
                }
                case "resource_link" -> text.append(block.path("uri").asText("")).append('\n');
                default -> text.append(block.toString()).append('\n');
            }
        }
        Map<String, Object> structured = null;
        JsonNode structuredNode = result.get("structuredContent");
        if (structuredNode != null && structuredNode.isObject()) {
            structured = objectMapper.convertValue(structuredNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        }
        return new McpToolCallResult(true, isError, text.toString().trim(),
                structured == null ? Map.of() : structured, isError ? "远端工具返回错误" : null);
    }

    private Session open(McpEndpoint endpoint) {
        URI uri = OutboundUrlGuard.validate(endpoint.endpointUrl(), "MCP 服务地址",
                endpoint.allowPrivateNetwork() || (outboundNetworkPolicy != null && outboundNetworkPolicy.allowPrivateNetwork()));
        Session session = new Session(endpoint, uri);
        session.initialize();
        return session;
    }

    /** 单次操作内的 MCP 会话：持有 initialize 协商结果与 Mcp-Session-Id。 */
    private final class Session implements AutoCloseable {

        private final McpEndpoint endpoint;
        private final URI uri;
        private final int timeoutMillis;
        private String sessionId;
        private String protocolVersion;
        private String serverName;
        private String serverVersion;

        private Session(McpEndpoint endpoint, URI uri) {
            this.endpoint = endpoint;
            this.uri = uri;
            this.timeoutMillis = endpoint.timeoutMillis();
        }

        private void initialize() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("protocolVersion", PROTOCOL_VERSION);
            params.put("capabilities", Map.of("tools", Map.of()));
            params.put("clientInfo", Map.of("name", CLIENT_NAME, "version", CLIENT_VERSION));
            try {
                applyInitializeRequest(PROTOCOL_VERSION, params);
            } catch (McpProtocolException unsupported) {
                if (!unsupported.versionFallback) {
                    throw unsupported;
                }
                applyInitializeRequest(FALLBACK_PROTOCOL_VERSION, params);
            }
            // 完成初始化握手的通知：规范要求初始化后立即发送，不等待响应。
            notify("notifications/initialized");
        }

        private void applyInitializeRequest(String version, Map<String, Object> params) {
            params.put("protocolVersion", version);
            JsonNode result = requestInternal("initialize", params, true);
            this.protocolVersion = result.path("protocolVersion").asText(version);
            JsonNode serverInfo = result.path("serverInfo");
            this.serverName = serverInfo.path("name").asText(null);
            this.serverVersion = serverInfo.path("version").asText(null);
        }

        private List<McpToolDefinition> listTools() {
            List<McpToolDefinition> tools = new ArrayList<>();
            String cursor = null;
            for (int page = 0; page < MAX_TOOL_PAGES; page++) {
                Map<String, Object> params = new LinkedHashMap<>();
                if (cursor != null && !cursor.isBlank()) {
                    params.put("cursor", cursor);
                }
                JsonNode result = request("tools/list", params);
                for (JsonNode tool : result.path("tools")) {
                    McpToolDefinition definition = toDefinition(tool);
                    if (definition != null && !definition.name().isBlank()) {
                        tools.add(definition);
                    }
                }
                cursor = result.path("nextCursor").asText("");
                if (cursor.isBlank()) {
                    return tools;
                }
            }
            return tools;
        }

        private McpToolDefinition toDefinition(JsonNode tool) {
            String name = tool.path("name").asText("");
            if (name.isBlank()) {
                return null;
            }
            JsonNode annotations = tool.path("annotations");
            boolean readOnly = annotations.path("readOnlyHint").asBoolean(false);
            boolean destructive = annotations.path("destructiveHint").asBoolean(false);
            Map<String, Object> schema = tool.has("inputSchema") && tool.get("inputSchema").isObject()
                    ? objectMapper.convertValue(tool.get("inputSchema"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    })
                    : Map.of();
            return new McpToolDefinition(
                    name,
                    tool.path("title").asText(name),
                    tool.path("description").asText(""),
                    schema,
                    readOnly ? "READ" : "WRITE",
                    destructive
            );
        }

        private JsonNode request(String method, Map<String, Object> params) {
            return requestInternal(method, params, false);
        }

        private JsonNode requestInternal(String method, Map<String, Object> params, boolean allowVersionFallback) {
            int id = nextId();
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("id", id);
            payload.put("method", method);
            payload.set("params", objectMapper.valueToTree(params == null ? Map.of() : params));
            HttpResponse<InputStream> response = post(payload.toString());
            String body = readBody(response);
            int status = response.statusCode();
            if (status >= 400) {
                throw new McpProtocolException(statusMessage(status, body), allowVersionFallback);
            }
            JsonNode message = extractMessage(body, response, id);
            if (message == null) {
                throw new McpProtocolException("MCP 服务未返回匹配的 JSON-RPC 响应（" + method + "）", false);
            }
            JsonNode error = message.get("error");
            if (error != null && !error.isNull()) {
                throw new McpProtocolException("MCP 服务返回错误：" + error.path("message").asText(error.toString()),
                        allowVersionFallback);
            }
            return message.path("result");
        }

        private void notify(String method) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("method", method);
            payload.set("params", objectMapper.createObjectNode());
            readBody(post(payload.toString()));
        }

        private HttpResponse<InputStream> post(String body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (sessionId != null && !sessionId.isBlank()) {
                builder.header("Mcp-Session-Id", sessionId);
            }
            if (protocolVersion != null && !protocolVersion.isBlank()) {
                builder.header("MCP-Protocol-Version", protocolVersion);
            }
            endpoint.headers().forEach((key, value) -> {
                if (key != null && value != null && !key.isBlank()) {
                    builder.header(key.trim(), value);
                }
            });
            try {
                HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                String session = response.headers().firstValue("Mcp-Session-Id").orElse("");
                if (!session.isBlank()) {
                    this.sessionId = session;
                }
                return response;
            } catch (java.io.IOException exception) {
                throw new BizException("MCP 服务连接失败：" + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BizException("MCP 服务调用被中断");
            }
        }

        /** 读取响应体：SSE 长连接可能不主动结束，因此强制读超时并在超时后关闭连接。 */
        private String readBody(HttpResponse<InputStream> response) {
            InputStream stream = response.body();
            if (stream == null) {
                return "";
            }
            try {
                Future<String> reader = bodyReaders.submit(() -> new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                return reader.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException exception) {
                closeQuietly(stream);
                throw new BizException("MCP 服务响应超时（" + timeoutMillis + "ms）");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                closeQuietly(stream);
                throw new BizException("MCP 服务调用被中断");
            } catch (java.util.concurrent.ExecutionException exception) {
                throw new BizException("MCP 服务响应读取失败：" + exception.getCause().getMessage());
            } catch (Exception exception) {
                throw new BizException("MCP 服务响应读取失败：" + exception.getMessage());
            }
        }

        private JsonNode extractMessage(String body, HttpResponse<InputStream> response, int id) {
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (body == null || body.isBlank()) {
                return null;
            }
            if (contentType.toLowerCase().contains("text/event-stream") || body.startsWith("event:") || body.startsWith("data:")) {
                for (String data : sseDataPayloads(body)) {
                    JsonNode message = parse(data);
                    if (message != null && message.path("id").asInt(Integer.MIN_VALUE) == id) {
                        return message;
                    }
                }
                return null;
            }
            JsonNode message = parse(body);
            if (message == null) {
                return null;
            }
            if (message.path("id").asInt(Integer.MIN_VALUE) == id) {
                return message;
            }
            // 部分服务端省略 id 或返回批量消息：单条且无 id 时按同一响应处理。
            return message.has("id") || message.isArray() ? null : message;
        }

        private List<String> sseDataPayloads(String body) {
            List<String> payloads = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String line : body.split("\r?\n")) {
                if (line.startsWith("data:")) {
                    current.append(line.substring(5).trim());
                } else if (line.isBlank() && current.length() > 0) {
                    payloads.add(current.toString());
                    current.setLength(0);
                }
            }
            if (current.length() > 0) {
                payloads.add(current.toString());
            }
            return payloads;
        }

        private JsonNode parse(String text) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception exception) {
                return null;
            }
        }

        private String statusMessage(int status, String body) {
            JsonNode message = parse(body);
            if (message != null) {
                JsonNode error = message.get("error");
                if (error != null && !error.isNull()) {
                    return "MCP 服务返回 HTTP " + status + "：" + error.path("message").asText(error.toString());
                }
            }
            String detail = body == null ? "" : body.trim();
            if (detail.length() > 200) {
                detail = detail.substring(0, 200) + "…";
            }
            return "MCP 服务返回 HTTP " + status + (detail.isBlank() ? "" : "：" + detail);
        }

        private void closeQuietly(InputStream stream) {
            try {
                stream.close();
            } catch (Exception ignored) {
                // 关闭失败不影响报错
            }
        }

        @Override
        public void close() {
            // 单次操作结束即释放；HTTP 层不缓存连接会话。
            sessionId = null;
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger REQUEST_ID = new java.util.concurrent.atomic.AtomicInteger(1);

    private static int nextId() {
        return REQUEST_ID.getAndIncrement();
    }

    /** 协议层错误；版本协商失败时允许回退到旧协议版本重试。 */
    private static final class McpProtocolException extends RuntimeException {
        private final boolean versionFallback;

        private McpProtocolException(String message, boolean versionFallback) {
            super(message);
            this.versionFallback = versionFallback;
        }
    }
}
