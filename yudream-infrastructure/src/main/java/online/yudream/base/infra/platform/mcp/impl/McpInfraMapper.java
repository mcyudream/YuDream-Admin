package online.yudream.base.infra.platform.mcp.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.mcp.aggregate.McpServer;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;
import online.yudream.base.infra.platform.mcp.dataobj.McpServerDO;

import java.util.List;

/** MCP 服务 DO ↔ 领域对象映射；工具快照以 JSON 字符串存储。 */
final class McpInfraMapper {

    private static final TypeReference<List<McpToolDefinition>> TOOL_LIST = new TypeReference<>() {
    };
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpInfraMapper() {
    }

    static McpServer toDomain(McpServerDO data) {
        if (data == null) {
            return null;
        }
        McpServer server = new McpServer();
        server.setId(data.getId());
        server.setVersion(data.getVersion());
        server.setCreateTime(data.getCreateTime());
        server.setUpdateTime(data.getUpdateTime());
        server.setCode(data.getCode());
        server.setName(data.getName());
        server.setDescription(data.getDescription());
        server.setTransport(data.getTransport());
        server.setEndpointUrl(data.getEndpointUrl());
        server.setHeaders(data.getHeaders());
        server.setEnabled(data.getEnabled());
        server.setPreload(data.getPreload());
        server.setTimeoutMillis(data.getTimeoutMillis());
        server.setAllowPrivateNetwork(data.getAllowPrivateNetwork());
        server.setLastStatus(data.getLastStatus());
        server.setLastError(data.getLastError());
        server.setLastConnectedAt(data.getLastConnectedAt());
        server.setProtocolVersion(data.getProtocolVersion());
        server.setServerName(data.getServerName());
        server.setServerVersion(data.getServerVersion());
        server.setTools(parseTools(data.getToolsJson()));
        return server;
    }

    static McpServerDO toDataObj(McpServer server) {
        if (server == null) {
            return null;
        }
        McpServerDO data = new McpServerDO();
        data.setId(server.getId());
        data.setVersion(server.getVersion());
        data.setCreateTime(server.getCreateTime());
        data.setUpdateTime(server.getUpdateTime());
        data.setCode(server.getCode());
        data.setName(server.getName());
        data.setDescription(server.getDescription());
        data.setTransport(server.getTransport());
        data.setEndpointUrl(server.getEndpointUrl());
        data.setHeaders(server.headersOrEmpty());
        data.setEnabled(server.getEnabled());
        data.setPreload(server.getPreload());
        data.setTimeoutMillis(server.getTimeoutMillis());
        data.setAllowPrivateNetwork(server.getAllowPrivateNetwork());
        data.setLastStatus(server.getLastStatus());
        data.setLastError(server.getLastError());
        data.setLastConnectedAt(server.getLastConnectedAt());
        data.setProtocolVersion(server.getProtocolVersion());
        data.setServerName(server.getServerName());
        data.setServerVersion(server.getServerVersion());
        data.setToolsJson(writeTools(server.toolsOrEmpty()));
        return data;
    }

    private static List<McpToolDefinition> parseTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<McpToolDefinition> tools = MAPPER.readValue(json, TOOL_LIST);
            return tools == null ? List.of() : List.copyOf(tools);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String writeTools(List<McpToolDefinition> tools) {
        try {
            return MAPPER.writeValueAsString(tools == null ? List.of() : tools);
        } catch (Exception exception) {
            return "[]";
        }
    }
}
