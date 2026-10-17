package online.yudream.base.infra.platform.mcp.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiToolModules;
import online.yudream.base.domain.platform.mcp.aggregate.McpServer;
import online.yudream.base.domain.platform.mcp.repo.McpServerRepo;
import online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider;
import online.yudream.base.domain.platform.mcp.service.McpClientGateway;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已启用 MCP 服务端缓存的工具清单投影为可注册的 Agent 工具。
 * 不在此处建连：连接只发生在管理端测试/刷新与真实工具调用时。
 */
@Service
@RequiredArgsConstructor
public class McpAgentToolProviderImpl implements McpAgentToolProvider {

    /** MCP 工具的统一调用权限码，避免为每次登记动态生成权限。 */
    public static final String PERMISSION_CODE = "platform:mcp:server:use";

    private final McpServerRepo serverRepo;
    private final McpClientGateway clientGateway;

    @Override
    public List<AiAgentTool> tools() {
        List<AiAgentTool> tools = new ArrayList<>();
        for (McpServer server : serverRepo.findAll()) {
            if (!server.isEnabled()) {
                continue;
            }
            for (McpToolDefinition definition : server.toolsOrEmpty()) {
                tools.add(new McpAgentTool(server, definition, clientGateway));
            }
        }
        return List.copyOf(tools);
    }

    @Override
    public AiAgentTool tool(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return null;
        }
        String code = toolCode.trim();
        for (McpServer server : serverRepo.findAll()) {
            if (!server.isEnabled()) {
                continue;
            }
            for (McpToolDefinition definition : server.toolsOrEmpty()) {
                if (code.equals(platformToolName(server.getCode(), definition.name()))) {
                    return new McpAgentTool(server, definition, clientGateway);
                }
            }
        }
        return null;
    }

    public static String platformToolName(String serverCode, String toolName) {
        return AiToolModules.mcpToolName(serverCode, toolName);
    }

    /** 单个 MCP 工具的 Agent 工具适配：每次调用都是独立的 MCP 会话序列。 */
    private static final class McpAgentTool implements AiAgentTool {

        private final McpServer server;
        private final McpToolDefinition definition;
        private final McpClientGateway clientGateway;
        private final AiAgentToolDescriptor descriptor;

        private McpAgentTool(McpServer server, McpToolDefinition definition, McpClientGateway clientGateway) {
            this.server = server;
            this.definition = definition;
            this.clientGateway = clientGateway;
            String serverLabel = server.getName() == null || server.getName().isBlank() ? server.getCode() : server.getName();
            this.descriptor = new AiAgentToolDescriptor(
                    platformToolName(server.getCode(), definition.name()),
                    definition.title(),
                    "[" + serverLabel + " · MCP] " + definition.description(),
                    PERMISSION_CODE,
                    "调用 MCP 工具",
                    "MCP 服务",
                    "调用外部 MCP 服务端提供的工具。",
                    definition.inputSchema(),
                    definition.inputSchema()
            );
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public String moduleCode() {
            return AiToolModules.mcpModuleName(server.getCode());
        }

        @Override
        public AgentToolRisk risk() {
            return "READ".equalsIgnoreCase(definition.risk()) ? AgentToolRisk.READ : AgentToolRisk.WRITE;
        }

        @Override
        public boolean requiresConfirmation() {
            return risk() != AgentToolRisk.READ || definition.requiresConfirmation();
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            Map<String, Object> arguments = call == null || call.arguments() == null
                    ? Map.of()
                    : new LinkedHashMap<>(call.arguments());
            McpClientGateway.McpToolCallResult result = clientGateway.callTool(server.endpoint(), definition.name(), arguments);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("server", server.getCode());
            payload.put("tool", definition.name());
            payload.put("content", result.content() == null ? "" : result.content());
            if (result.structured() != null && !result.structured().isEmpty()) {
                payload.put("structured", result.structured());
            }
            if (!result.success() || result.isError()) {
                payload.put("error", result.error() == null ? "MCP 工具调用失败" : result.error());
                return new AiAgentToolResult(descriptor.name(), "error", PERMISSION_CODE,
                        result.error() == null ? "MCP 工具调用失败" : result.error(), payload);
            }
            return new AiAgentToolResult(descriptor.name(), "mcp", PERMISSION_CODE,
                    "MCP 工具已返回结果。", payload);
        }
    }
}
