package online.yudream.base.interfaces.platform.mcpserver.protocol;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.mcpserver.service.McpServerAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcpserver.service.McpToolSource;
import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpJsonRpcSupportTest {

    private static final McpToolPrincipal ADMIN = new McpToolPrincipal(1L, List.of("*"));

    private static McpToolSource source() {
        return new McpToolSource() {
            @Override
            public List<McpToolModuleRef> modules() {
                return List.of(new McpToolModuleRef("mcpanel", "MC 面板"));
            }

            @Override
            public List<McpExposedTool> exposedTools(String module) {
                return List.of(
                        new McpExposedTool("mcpanel_instance_list", "mcpanel", "实例列表",
                                "列出实例", "plugin:mcpanel:view",
                                Map.of("type", "object", "properties", Map.of()), false),
                        new McpExposedTool("mcpanel_boom", "mcpanel", "失败样例",
                                "总是失败", "plugin:mcpanel:view",
                                Map.of("type", "object"), false));
            }

            @Override
            public McpToolOutcome call(String module, String toolName, Map<String, Object> arguments,
                                       McpToolPrincipal principal) {
                if ("mcpanel_boom".equals(toolName)) {
                    return new McpToolOutcome(true, "实例启动失败");
                }
                return new McpToolOutcome(false, "ok");
            }
        };
    }

    private static McpJsonRpcSupport support() {
        CapabilityAppService capability = new CapabilityAppService(null, List.of()) {
            @Override
            public void ensureEnabled(String code, String name) {
            }
        };
        return new McpJsonRpcSupport(new McpServerAppService(capability, source()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<Map<String, Object>> response) {
        return response.getBody();
    }

    @Test
    void initializeEchoesSupportedVersionAndDescribesServer() {
        ResponseEntity<Map<String, Object>> response = support().handle("mcpanel",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}", ADMIN);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> result = (Map<String, Object>) body(response).get("result");
        assertEquals("2025-03-26", result.get("protocolVersion"));
        assertEquals("yudream-mcp:mcpanel", ((Map<String, Object>) result.get("serverInfo")).get("name"));
        String instructions = (String) result.get("instructions");
        assertTrue(instructions.contains("mcpanel"));
    }

    @Test
    void initializeFallsBackToLatestForUnknownVersion() {
        ResponseEntity<Map<String, Object>> response = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}", ADMIN);
        Map<String, Object> result = (Map<String, Object>) body(response).get("result");
        assertEquals(McpJsonRpcSupport.PROTOCOL_VERSION, result.get("protocolVersion"));
        assertEquals("yudream-mcp", ((Map<String, Object>) result.get("serverInfo")).get("name"));
    }

    @Test
    void notificationReturns202() {
        ResponseEntity<Map<String, Object>> response = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", ADMIN);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void toolsListShapesMcpToolObjects() {
        ResponseEntity<Map<String, Object>> response = support().handle("mcpanel",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", ADMIN);
        Map<String, Object> result = (Map<String, Object>) body(response).get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(2, tools.size());
        assertEquals("mcpanel_instance_list", tools.get(0).get("name"));
        assertEquals("实例列表", tools.get(0).get("title"));
        assertTrue(((Map<String, Object>) tools.get(0).get("inputSchema")).containsKey("type"));
    }

    @Test
    void toolsCallReturnsContentAndErrorFlag() {
        ResponseEntity<Map<String, Object>> ok = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"mcpanel_instance_list\",\"arguments\":{}}}", ADMIN);
        Map<String, Object> result = (Map<String, Object>) body(ok).get("result");
        assertEquals(Boolean.FALSE, result.get("isError"));
        assertEquals("ok", ((Map<String, Object>) ((List<Object>) result.get("content")).get(0)).get("text"));

        ResponseEntity<Map<String, Object>> failed = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"mcpanel_boom\"}}", ADMIN);
        Map<String, Object> failedResult = (Map<String, Object>) body(failed).get("result");
        assertEquals(Boolean.TRUE, failedResult.get("isError"));
        assertEquals("实例启动失败", ((Map<String, Object>) ((List<Object>) failedResult.get("content")).get(0)).get("text"));
    }

    @Test
    void unknownToolYieldsInBandError() {
        ResponseEntity<Map<String, Object>> response = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"no_such\"}}", ADMIN);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) body(response).get("error");
        assertEquals(-32000, error.get("code"));
        assertTrue(((String) error.get("message")).contains("no_such"));
    }

    @Test
    void unknownMethodParseErrorAndInvalidRequest() {
        ResponseEntity<Map<String, Object>> unknown = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/read\"}", ADMIN);
        assertEquals(-32601, ((Map<String, Object>) body(unknown).get("error")).get("code"));

        ResponseEntity<Map<String, Object>> parse = support().handle(null, "{not json", ADMIN);
        assertEquals(HttpStatus.BAD_REQUEST, parse.getStatusCode());
        assertEquals(-32700, ((Map<String, Object>) body(parse).get("error")).get("code"));

        ResponseEntity<Map<String, Object>> batch = support().handle(null, "[1,2,3]", ADMIN);
        assertEquals(HttpStatus.BAD_REQUEST, batch.getStatusCode());
        assertEquals(-32600, ((Map<String, Object>) body(batch).get("error")).get("code"));
    }

    @Test
    void disabledCapabilitySurfacesServerErrorMessage() {
        CapabilityAppService capability = new CapabilityAppService(null, List.of()) {
            @Override
            public void ensureEnabled(String code, String name) {
                throw new BizException(name + "能力未启用，请先在平台能力中启用");
            }
        };
        McpJsonRpcSupport disabled = new McpJsonRpcSupport(new McpServerAppService(capability, source()));
        ResponseEntity<Map<String, Object>> response = disabled.handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\"}", ADMIN);
        assertEquals(-32000, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertFalse(body(response).containsKey("result"));
    }
}
