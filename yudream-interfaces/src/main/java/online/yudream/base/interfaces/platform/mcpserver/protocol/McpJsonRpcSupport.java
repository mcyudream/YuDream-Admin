package online.yudream.base.interfaces.platform.mcpserver.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mcpserver.service.McpServerAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP Streamable HTTP（纯 JSON 响应，无 server→client 流）的 JSON-RPC 2.0 协议分发。
 * 支持 initialize / ping / tools/list / tools/call，通知回 202；GET/DELETE 由控制器直接 405。
 */
@Component
@RequiredArgsConstructor
public class McpJsonRpcSupport {

    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("2024-11-05", "2025-03-26", "2025-06-18");

    private final McpServerAppService appService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResponseEntity<Map<String, Object>> handle(String module, String rawBody, McpToolPrincipal principal) {
        JsonNode root;
        try {
            root = mapper.readTree(rawBody == null ? "" : rawBody);
        } catch (Exception ex) {
            return error(null, -32700, "Parse error", HttpStatus.BAD_REQUEST);
        }
        if (root == null || !root.isObject() || !root.hasNonNull("method")) {
            return error(null, -32600, "Invalid Request", HttpStatus.BAD_REQUEST);
        }
        String method = root.get("method").asText();
        JsonNode params = root.get("params");
        JsonNode id = root.get("id");
        boolean notification = id == null || id.isNull();
        if (notification) {
            // 通知（含 notifications/initialized）：202 无 body
            return ResponseEntity.accepted().<Map<String, Object>>build();
        }
        try {
            switch (method) {
                case "initialize" -> {
                    return ok(id, initializeResult(module, params, principal));
                }
                case "ping" -> {
                    return ok(id, Map.of());
                }
                case "tools/list" -> {
                    return ok(id, toolsList(module, principal));
                }
                case "tools/call" -> {
                    return ok(id, toolsCall(module, params, principal));
                }
                case "resources/list" -> {
                    return ok(id, Map.of("resources", List.of()));
                }
                case "prompts/list" -> {
                    return ok(id, Map.of("prompts", List.of()));
                }
                default -> {
                    return error(id, -32601, "Method not found: " + method, HttpStatus.OK);
                }
            }
        } catch (BizException ex) {
            return error(id, -32000, ex.getMessage(), HttpStatus.OK);
        }
    }

    private Map<String, Object> initializeResult(String module, JsonNode params, McpToolPrincipal principal) {
        String requested = params != null && params.hasNonNull("protocolVersion")
                ? params.get("protocolVersion").asText()
                : null;
        String protocolVersion = requested != null && SUPPORTED_VERSIONS.contains(requested)
                ? requested
                : PROTOCOL_VERSION;
        String serverName = module == null || module.isBlank() ? "yudream-mcp" : "yudream-mcp:" + module;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of("name", serverName, "version", "1.0.0"));
        result.put("instructions", instructions(module, principal));
        return result;
    }

    private String instructions(String module, McpToolPrincipal principal) {
        StringBuilder text = new StringBuilder(
                "宿主 MCP 网关：工具按调用者权限过滤，只能看到、调用自己有权限的工具；"
                        + "写操作工具凭 API Key 授权的权限码放行。模块端点 /api/mcp/{module} 只含单模块。当前可见模块：\n");
        try {
            McpServerAppService.McpCatalog catalog = appService.catalog(module, principal.permissions());
            for (String summary : catalog.moduleSummaries()) {
                text.append("- ").append(summary).append('\n');
            }
        } catch (BizException ignored) {
            text.append("- （工具目录需认证后获取）");
        }
        return text.toString();
    }

    private Map<String, Object> toolsList(String module, McpToolPrincipal principal) {
        McpServerAppService.McpCatalog catalog = appService.catalog(module, principal.permissions());
        List<Map<String, Object>> tools = new ArrayList<>();
        for (var tool : catalog.tools()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("title", tool.title());
            item.put("description", tool.description());
            item.put("inputSchema", tool.inputSchema());
            tools.add(item);
        }
        return Map.of("tools", tools);
    }

    private Map<String, Object> toolsCall(String module, JsonNode params, McpToolPrincipal principal) {
        if (params == null || !params.hasNonNull("name")) {
            throw new BizException("缺少 params.name");
        }
        String toolName = params.get("name").asText();
        Map<String, Object> arguments = Map.of();
        JsonNode argsNode = params.get("arguments");
        if (argsNode != null && argsNode.isObject()) {
            arguments = mapper.convertValue(argsNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        }
        McpToolOutcome outcome = appService.callTool(module, toolName, arguments, principal);
        Map<String, Object> content = Map.of("type", "text", "text", outcome.text());
        return Map.of("content", List.of(content), "isError", outcome.error());
    }

    private ResponseEntity<Map<String, Object>> ok(JsonNode id, Map<String, Object> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
    }

    private ResponseEntity<Map<String, Object>> error(JsonNode id, int code, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", Map.of("code", code, "message", message == null ? "" : message));
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body);
    }
}
