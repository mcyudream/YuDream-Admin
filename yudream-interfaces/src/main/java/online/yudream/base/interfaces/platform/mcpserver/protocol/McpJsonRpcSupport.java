package online.yudream.base.interfaces.platform.mcpserver.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
 * 支持 initialize / ping / tools/list / tools/call；通过能力与格式校验的通知回 202。
 * GET/DELETE 由控制器直接 405。
 */
@Component
@RequiredArgsConstructor
public class McpJsonRpcSupport {

    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("2024-11-05", "2025-03-26", "2025-06-18");
    private static final Set<String> APPLICATION_GATED_METHODS = Set.of("initialize", "tools/list", "tools/call");

    private final McpServerAppService appService;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ResponseEntity<Map<String, Object>> handle(String module, String rawBody, McpToolPrincipal principal) {
        JsonNode root;
        try {
            root = mapper.readTree(rawBody == null ? "" : rawBody);
        } catch (Exception ex) {
            return error(null, -32700, "Parse error", HttpStatus.BAD_REQUEST);
        }
        if (root == null || root.isMissingNode()) {
            return error(null, -32700, "Parse error", HttpStatus.BAD_REQUEST);
        }
        if (!validEnvelope(root)) {
            return error(null, -32600, "Invalid Request", HttpStatus.BAD_REQUEST);
        }
        String method = root.get("method").asText();
        JsonNode params = root.get("params");
        JsonNode id = root.get("id");
        boolean notification = id == null;
        try {
            String paramsError = validateParams(method, params);
            // 正常握手、目录与调用由各自应用用例检查，避免重复查询能力状态。
            // 通知、无应用用例的方法和参数错误路径在入口检查。
            if (notification || paramsError != null || !APPLICATION_GATED_METHODS.contains(method)) {
                appService.ensureEnabled();
            }
            if (paramsError != null) {
                return error(id, -32602, paramsError,
                        notification ? HttpStatus.BAD_REQUEST : HttpStatus.OK, !notification);
            }
            if (notification) {
                // 通知不执行请求用例；仅在闸门与格式校验通过后接受。
                return ResponseEntity.accepted().<Map<String, Object>>build();
            }
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
            return error(id, -32000, ex.getMessage(),
                    notification ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK, !notification);
        }
    }

    private boolean validEnvelope(JsonNode root) {
        if (!root.isObject() || !"2.0".equals(root.path("jsonrpc").textValue())
                || root.has("result") || root.has("error")) {
            return false;
        }
        JsonNode method = root.get("method");
        JsonNode id = root.get("id");
        return method != null && method.isTextual() && !method.textValue().isBlank()
                && (id == null || id.isTextual() || id.isIntegralNumber());
    }

    private String validateParams(String method, JsonNode params) {
        if (params != null && !params.isObject()) {
            return "params 必须是 JSON 对象";
        }
        if ("initialize".equals(method) && params != null && params.has("protocolVersion")) {
            JsonNode version = params.get("protocolVersion");
            if (!version.isTextual() || version.textValue().isBlank()) {
                return "params.protocolVersion 必须是非空字符串";
            }
        }
        if ("tools/call".equals(method)) {
            JsonNode name = params == null ? null : params.get("name");
            if (name == null || !name.isTextual() || name.textValue().isBlank()) {
                return "params.name 必须是非空工具名字符串";
            }
            JsonNode arguments = params.get("arguments");
            if (arguments != null && !arguments.isObject()) {
                return "params.arguments 必须是 JSON 对象；无参数时请省略或传 {}";
            }
        }
        return null;
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
                        + "写操作也需对应权限授权。模块端点 /api/mcp/{module} 只含单模块。当前可见模块：\n");
        McpServerAppService.McpCatalog catalog = appService.catalog(module, principal.permissions());
        for (String summary : catalog.moduleSummaries()) {
            text.append("- ").append(summary).append('\n');
        }
        if (catalog.tools().isEmpty()) {
            text.append("- 当前账号或凭据下没有可见工具，请检查模块、工具的 MCP 通道声明及对应权限授权。\n");
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
            item.put("annotations", Map.of("readOnlyHint", !tool.write(), "destructiveHint", false));
            tools.add(item);
        }
        return Map.of("tools", tools);
    }

    private Map<String, Object> toolsCall(String module, JsonNode params, McpToolPrincipal principal) {
        String toolName = params.get("name").textValue();
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
        return error(id, code, message, status, true);
    }

    private ResponseEntity<Map<String, Object>> error(JsonNode id, int code, String message, HttpStatus status,
                                                     boolean includeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        if (includeId) {
            body.put("id", id);
        }
        body.put("error", Map.of("code", code, "message", message == null ? "" : message));
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body);
    }
}
