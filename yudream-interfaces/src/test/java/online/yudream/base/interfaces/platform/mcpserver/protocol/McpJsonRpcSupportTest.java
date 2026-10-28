package online.yudream.base.interfaces.platform.mcpserver.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.mcpserver.service.McpServerAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcpserver.service.McpToolSource;
import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpJsonRpcSupportTest {

    private static final McpToolPrincipal ADMIN = new McpToolPrincipal(1L, List.of("*"));

    private static final class FakeSource implements McpToolSource {
        int calls;
        Map<String, Object> lastArguments;
        McpToolPrincipal lastPrincipal;

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
                            Map.of("type", "object"), false),
                    new McpExposedTool("mcpanel_instance_start", "mcpanel", "启动实例",
                            "启动指定实例", "plugin:mcpanel:manage",
                            Map.of("type", "object"), true));
        }

        @Override
        public McpToolOutcome call(String module, String toolName, Map<String, Object> arguments,
                                   McpToolPrincipal principal) {
            calls++;
            lastArguments = arguments;
            lastPrincipal = principal;
            if ("mcpanel_boom".equals(toolName)) {
                return new McpToolOutcome(true, "实例启动失败");
            }
            return new McpToolOutcome(false, "ok");
        }
    }

    private static FakeSource source() {
        return new FakeSource();
    }

    private static McpJsonRpcSupport support() {
        return support(source());
    }

    private static McpJsonRpcSupport support(McpToolSource source) {
        CapabilityAppService capability = new CapabilityAppService(null, List.of()) {
            @Override
            public void ensureEnabled(String code, String name) {
            }
        };
        return new McpJsonRpcSupport(new McpServerAppService(capability, source));
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
        assertNull(response.getBody());
    }

    @Test
    void toolsListShapesMcpToolObjects() {
        ResponseEntity<Map<String, Object>> response = support().handle("mcpanel",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", ADMIN);
        Map<String, Object> result = (Map<String, Object>) body(response).get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(3, tools.size());
        assertEquals("mcpanel_instance_list", tools.get(0).get("name"));
        assertEquals("实例列表", tools.get(0).get("title"));
        assertTrue(((Map<String, Object>) tools.get(0).get("inputSchema")).containsKey("type"));
        assertEquals(Boolean.TRUE, ((Map<String, Object>) tools.get(0).get("annotations")).get("readOnlyHint"));
        assertEquals(Boolean.FALSE, ((Map<String, Object>) tools.get(2).get("annotations")).get("readOnlyHint"));
        assertEquals(Boolean.FALSE, ((Map<String, Object>) tools.get(2).get("annotations")).get("destructiveHint"));
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

    private static McpJsonRpcSupport disabled() {
        CapabilityAppService capability = new CapabilityAppService(null, List.of()) {
            @Override
            public void ensureEnabled(String code, String name) {
                throw new BizException(name + "能力未启用，请先在平台能力中启用");
            }
        };
        return new McpJsonRpcSupport(new McpServerAppService(capability, source()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"initialize", "ping", "tools/list", "tools/call", "resources/list", "prompts/list", "unknown"})
    void disabledCapabilityRejectsEveryRequest(String method) {
        ResponseEntity<Map<String, Object>> response = disabled().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method + "\"}", ADMIN);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(-32000, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertTrue(((Map<String, Object>) body(response).get("error")).get("message").toString().contains("能力未启用"));
        assertFalse(body(response).containsKey("result"));
    }

    @Test
    void disabledCapabilityDoesNotAcceptNotifications() {
        ResponseEntity<Map<String, Object>> response = disabled().handle(null,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", ADMIN);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(-32000, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertFalse(body(response).containsKey("id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "true", "\"text\"",
            "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"  \"}",
            "{\"name\":123}", "{\"name\":true}", "{\"name\":{}}", "{\"name\":[]}",
            "{\"name\":\"mcpanel_instance_list\",\"arguments\":[]}",
            "{\"name\":\"mcpanel_instance_list\",\"arguments\":\"text\"}",
            "{\"name\":\"mcpanel_instance_list\",\"arguments\":42}",
            "{\"name\":\"mcpanel_instance_list\",\"arguments\":true}",
            "{\"name\":\"mcpanel_instance_list\",\"arguments\":null}"
    })
    void invalidToolParamsNeverReachToolSource(String params) {
        FakeSource source = source();
        ResponseEntity<Map<String, Object>> response = support(source).handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":\"call-1\",\"method\":\"tools/call\",\"params\":" + params + "}", ADMIN);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(-32602, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertEquals("call-1", ((JsonNode) body(response).get("id")).asText());
        assertEquals(0, source.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":1,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}",
            "{\"jsonrpc\":2,\"id\":1,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":123}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\" \"}",
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"tools/call\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"tools/call\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"tools/call\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":[],\"method\":\"tools/call\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1.5,\"method\":\"tools/call\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"result\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"error\":{}}"
    })
    void malformedEnvelopeIsRejectedBeforeExecution(String request) {
        FakeSource source = source();
        ResponseEntity<Map<String, Object>> response = support(source).handle(null, request, ADMIN);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(-32600, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertNull(body(response).get("id"));
        assertEquals(0, source.calls);
    }

    @Test
    void multipleJsonValuesAreRejectedBeforeExecution() {
        FakeSource source = source();
        ResponseEntity<Map<String, Object>> response = support(source).handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"mcpanel_instance_list\"}} {}", ADMIN);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(-32700, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertEquals(0, source.calls);
    }

    @Test
    void invalidNotificationParamsReturnHttpErrorWithoutResponseId() {
        ResponseEntity<Map<String, Object>> response = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":[]}", ADMIN);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(-32602, ((Map<String, Object>) body(response).get("error")).get("code"));
        assertFalse(body(response).containsKey("id"));
    }

    @Test
    void notificationsNeverInvokeTools() {
        FakeSource source = source();
        ResponseEntity<Map<String, Object>> response = support(source).handle(null,
                "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"mcpanel_instance_start\"}}", ADMIN);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNull(response.getBody());
        assertEquals(0, source.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "[]", "{}", "true", "\"\""})
    void initializeRejectsMalformedVersion(String version) {
        ResponseEntity<Map<String, Object>> response = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":" + version + "}}", ADMIN);
        assertEquals(-32602, ((Map<String, Object>) body(response).get("error")).get("code"));
    }

    @Test
    void clientLifecyclePreservesIdsArgumentsAndPermissions() {
        FakeSource source = source();
        McpJsonRpcSupport support = support(source);
        McpToolPrincipal viewer = new McpToolPrincipal(2L, List.of("plugin:mcpanel:view"));
        String id = "900719925474099312345";
        ResponseEntity<Map<String, Object>> initialized = support.handle("plugin:mcpanel", """
                {"jsonrpc":"2.0","id":"%s","method":"initialize","params":{
                  "protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test-client","version":"1"}
                }}
                """.formatted(id), viewer);
        assertEquals(id, ((JsonNode) body(initialized).get("id")).asText());
        assertTrue(body(initialized).containsKey("result"));
        assertEquals(HttpStatus.ACCEPTED, support.handle("plugin:mcpanel",
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", viewer).getStatusCode());

        ResponseEntity<Map<String, Object>> listed = support.handle("plugin:mcpanel",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", viewer);
        List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) body(listed).get("result")).get("tools");
        assertEquals(2, tools.size());
        assertTrue(tools.stream().noneMatch(tool -> tool.get("name").equals("mcpanel_instance_start")));

        ResponseEntity<Map<String, Object>> denied = support.handle("plugin:mcpanel",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"mcpanel_instance_start\"}}", viewer);
        assertTrue(body(denied).containsKey("error"));
        assertEquals(0, source.calls);

        ResponseEntity<Map<String, Object>> called = support.handle("plugin:mcpanel", """
                {"jsonrpc":"2.0","id":"%s","method":"tools/call","params":{
                  "name":"mcpanel_instance_list","arguments":{"instanceId":"%s","filter":{"running":true}}
                }}
                """.formatted(id, id), viewer);
        assertEquals(Boolean.FALSE, ((Map<String, Object>) body(called).get("result")).get("isError"));
        assertEquals(id, ((JsonNode) body(called).get("id")).asText());
        assertEquals(Map.of("instanceId", id, "filter", Map.of("running", true)), source.lastArguments);
        assertEquals(viewer, source.lastPrincipal);
        assertEquals(1, source.calls);
    }

    @Test
    void initializeExplainsEmptyCatalog() {
        ResponseEntity<Map<String, Object>> response = support().handle(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", new McpToolPrincipal(2L, List.of()));
        String instructions = (String) ((Map<String, Object>) body(response).get("result")).get("instructions");
        assertTrue(instructions.contains("没有可见工具"));
        assertFalse(instructions.contains("MC 面板"));
    }
}
