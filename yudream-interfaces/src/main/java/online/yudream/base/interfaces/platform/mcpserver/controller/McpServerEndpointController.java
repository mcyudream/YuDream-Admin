package online.yudream.base.interfaces.platform.mcpserver.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.interfaces.platform.mcpserver.protocol.McpJsonRpcSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 平台 MCP 服务器端点（能力 mcp-server）：
 * <ul>
 *   <li>{@code POST /api/mcp} —— 完整 MCP：聚合全部模块工具</li>
 *   <li>{@code POST /api/mcp/{module}} —— 模块 MCP：只含指定模块（模块名 = 插件 code）</li>
 * </ul>
 * 认证：API Key（{@code Authorization: Bearer yda_...}）、OAuth 或登录会话；
 * 工具可见与可调用按调用者权限过滤。无 server→client 流：GET/DELETE 一律 405。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mcp")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mcp-server", name = "enabled", havingValue = "true")
public class McpServerEndpointController {

    private final McpJsonRpcSupport jsonRpc;

    @PostMapping
    public ResponseEntity<Map<String, Object>> full(@RequestBody(required = false) String body) {
        return dispatch(null, body);
    }

    @PostMapping("/{module}")
    public ResponseEntity<Map<String, Object>> byModule(@PathVariable String module,
                                                        @RequestBody(required = false) String body) {
        return dispatch(module, body);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getFull() {
        return methodNotAllowed();
    }

    @GetMapping("/{module}")
    public ResponseEntity<Map<String, Object>> getModule() {
        return methodNotAllowed();
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteFull() {
        return methodNotAllowed();
    }

    @DeleteMapping("/{module}")
    public ResponseEntity<Map<String, Object>> deleteModule() {
        return methodNotAllowed();
    }

    private ResponseEntity<Map<String, Object>> dispatch(String module, String body) {
        SecurityPrincipalSupport.SecurityPrincipal principal;
        try {
            principal = SecurityPrincipalSupport.current();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", Map.of(
                            "code", -32000,
                            "message", "未认证：请以 API Key（Authorization: Bearer yda_...）或登录态访问 MCP 端点")));
        }
        return jsonRpc.handle(module, body,
                new McpToolPrincipal(principal.userId(), principal.permissions()));
    }

    private ResponseEntity<Map<String, Object>> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", "POST")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "MCP 端点仅接受 POST（Streamable HTTP，纯 JSON 响应，无 SSE 流）"));
    }
}
