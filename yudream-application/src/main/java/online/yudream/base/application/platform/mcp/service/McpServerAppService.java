package online.yudream.base.application.platform.mcp.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiToolModuleAppService;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.mcp.cmd.McpServerSaveCmd;
import online.yudream.base.application.platform.mcp.dto.McpServerDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiToolModules;
import online.yudream.base.domain.platform.mcp.aggregate.McpServer;
import online.yudream.base.domain.platform.mcp.enumerate.McpConnectionStatus;
import online.yudream.base.domain.platform.mcp.enumerate.McpTransport;
import online.yudream.base.domain.platform.mcp.repo.McpServerRepo;
import online.yudream.base.domain.platform.mcp.service.McpClientGateway;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务端管理：注册连接参数、连接测试、工具清单刷新。
 * 连接测试与工具刷新才真正建连；运行期调用由 {@code McpAgentToolProvider} 按需发起。
 */
@Service
@RequiredArgsConstructor
public class McpServerAppService {

    private static final String CAPABILITY = "mcp";
    /** 请求头回显掩码：保存时该值表示保留原凭据。 */
    public static final String HEADER_MASK = "••••••";

    private final McpServerRepo serverRepo;
    private final McpClientGateway clientGateway;
    private final CapabilityAppService capabilityAppService;
    private final AiToolModuleAppService toolModuleAppService;

    public List<McpServerDTO> list() {
        ensureEnabled();
        return serverRepo.findAll().stream().map(this::toDTO).toList();
    }

    public McpServerDTO create(McpServerSaveCmd cmd) {
        ensureEnabled();
        McpServer server = McpServer.create(cmd.getName(), cmd.getCode());
        apply(server, cmd);
        serverRepo.findByCode(server.getCode()).ifPresent(existing -> {
            throw new BizException("MCP 服务编码已存在：" + server.getCode());
        });
        McpServer saved = serverRepo.save(server);
        return probeAndSave(saved);
    }

    public McpServerDTO update(McpServerSaveCmd cmd) {
        ensureEnabled();
        McpServer server = server(cmd.getId());
        String previousCode = server.getCode();
        apply(server, cmd, true);
        if (!server.getCode().equals(previousCode)) {
            serverRepo.findByCode(server.getCode()).ifPresent(existing -> {
                throw new BizException("MCP 服务编码已存在：" + server.getCode());
            });
            // 编码变更即换模块：清空旧工具快照，避免旧模块编码下的工具残留。
            server.replaceTools(List.of());
        }
        return toDTO(serverRepo.save(server));
    }

    public void delete(Long id) {
        ensureEnabled();
        server(id);
        serverRepo.deleteById(id);
    }

    /** 连接测试：完成 initialize 与 tools/list，并把结果写入连接状态与工具快照。 */
    public McpServerDTO test(Long id) {
        ensureEnabled();
        return probeAndSave(server(id));
    }

    private McpServerDTO probeAndSave(McpServer server) {
        McpClientGateway.McpProbeResult result = clientGateway.probe(server.endpoint());
        if (!result.success()) {
            server.markFailed(result.message());
            return toDTO(serverRepo.save(server));
        }
        server.markConnected(result);
        return toDTO(serverRepo.save(server));
    }

    private void apply(McpServer server, McpServerSaveCmd cmd) {
        apply(server, cmd, false);
    }

    private void apply(McpServer server, McpServerSaveCmd cmd, boolean keepHeaders) {
        Map<String, String> headers = keepHeaders ? mergeHeaders(server.headersOrEmpty(), cmd.getHeaders()) : cmd.getHeaders();
        server.update(cmd.getName(), cmd.getCode(), cmd.getDescription(), transport(cmd.getTransport()),
                cmd.getEndpointUrl(), headers, cmd.getEnabled(), cmd.getPreload(), cmd.getTimeoutMillis(),
                cmd.getAllowPrivateNetwork());
    }

    /** 掩码值代表保留原凭据；空值删除该头的原值。 */
    private Map<String, String> mergeHeaders(Map<String, String> existing, Map<String, String> incoming) {
        if (incoming == null) {
            return existing;
        }
        if (incoming.isEmpty()) {
            return Map.of();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        incoming.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            String previous = existing.get(key.trim());
            if (value == null || value.isBlank() || HEADER_MASK.equals(value.trim())) {
                if (previous != null && !previous.isBlank()) {
                    merged.put(key.trim(), previous);
                }
                return;
            }
            merged.put(key.trim(), value.trim());
        });
        return merged;
    }

    private McpTransport transport(String value) {
        if (value == null || value.isBlank()) {
            return McpTransport.STREAMABLE_HTTP;
        }
        try {
            return McpTransport.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BizException("不支持的 MCP 传输类型：" + value + "（当前仅支持 STREAMABLE_HTTP）");
        }
    }

    private McpServer server(Long id) {
        if (id == null) {
            throw new BizException("MCP 服务标识不能为空");
        }
        return serverRepo.findById(id).orElseThrow(() -> new BizException("MCP 服务不存在"));
    }

    private McpServerDTO toDTO(McpServer server) {
        List<McpServerDTO.Tool> tools = new ArrayList<>();
        for (McpToolDefinition definition : server.toolsOrEmpty()) {
            tools.add(new McpServerDTO.Tool(
                    definition.name(),
                    definition.title(),
                    definition.description(),
                    definition.risk(),
                    definition.requiresConfirmation(),
                    AiToolModules.mcpToolName(server.getCode(), definition.name())
            ));
        }
        String moduleCode = AiToolModules.mcpModuleName(server.getCode());
        Map<String, String> headers = new LinkedHashMap<>();
        server.headersOrEmpty().forEach((key, value) -> headers.put(key, value == null || value.isBlank() ? "" : HEADER_MASK));
        return new McpServerDTO(
                server.getId(),
                server.getCode(),
                server.getName(),
                server.getDescription(),
                (server.getTransport() == null ? McpTransport.STREAMABLE_HTTP : server.getTransport()).name(),
                server.getEndpointUrl(),
                headers,
                server.isEnabled(),
                server.isPreload(),
                server.getTimeoutMillis() == null ? 15000 : server.getTimeoutMillis(),
                server.getAllowPrivateNetwork() == null || server.getAllowPrivateNetwork(),
                (server.getLastStatus() == null ? McpConnectionStatus.UNKNOWN : server.getLastStatus()).name(),
                server.getLastError(),
                server.getLastConnectedAt(),
                server.getProtocolVersion(),
                server.getServerName(),
                server.getServerVersion(),
                tools,
                moduleCode,
                toolModuleAppService.moduleOpen(moduleCode),
                server.getUpdateTime()
        );
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY, "MCP 服务接入");
    }
}
