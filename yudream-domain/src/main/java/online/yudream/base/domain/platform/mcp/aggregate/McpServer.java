package online.yudream.base.domain.platform.mcp.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcp.enumerate.McpConnectionStatus;
import online.yudream.base.domain.platform.mcp.enumerate.McpTransport;
import online.yudream.base.domain.platform.mcp.service.McpClientGateway;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务端注册记录：管理端维护连接参数，工具清单由连接测试/刷新缓存，
 * 运行期桥接为 <code>mcp.&lt;code&gt;.&lt;tool&gt;</code> 形式的 Agent 工具。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class McpServer extends BaseDomain {

    /** 服务编码：小写字母/数字/下划线/短横线，参与模块编码 mcp:&lt;code&gt;。 */
    private String code;
    private String name;
    private String description;
    private McpTransport transport;
    private String endpointUrl;
    private Map<String, String> headers;
    /** 是否参与运行：关闭后工具不注册。 */
    private Boolean enabled;
    /** 常驻模块：开启后工具无需 load_tools 加载，直接进入模型视野。 */
    private Boolean preload;
    /** 单次请求超时（毫秒）。 */
    private Integer timeoutMillis;
    /** 允许连接私网/回环地址（自建 MCP 服务常用），默认允许；仅管理员可配置。 */
    private Boolean allowPrivateNetwork;
    private McpConnectionStatus lastStatus;
    private String lastError;
    private LocalDateTime lastConnectedAt;
    private String protocolVersion;
    private String serverName;
    private String serverVersion;
    /** tools/list 快照。 */
    private List<McpToolDefinition> tools;

    /**
     * 新建服务：只校验标识字段，连接参数由随后的 update 落位
     * （不能走完整 update，否则会在地址尚未赋值时触发「地址不能为空」）。
     */
    public static McpServer create(String name, String code) {
        McpServer server = new McpServer();
        server.name = required(name, "MCP 服务名称不能为空");
        server.code = code(code);
        server.transport = McpTransport.STREAMABLE_HTTP;
        server.headers = Map.of();
        server.enabled = true;
        server.preload = false;
        server.timeoutMillis = 15000;
        server.allowPrivateNetwork = true;
        server.lastStatus = McpConnectionStatus.UNKNOWN;
        return server;
    }

    public void update(String name, String code, String description, McpTransport transport, String endpointUrl,
                       Map<String, String> headers, Boolean enabled, Boolean preload, Integer timeoutMillis,
                       Boolean allowPrivateNetwork) {
        this.name = required(name, "MCP 服务名称不能为空");
        this.code = code(code);
        this.description = description;
        this.transport = transport == null ? McpTransport.STREAMABLE_HTTP : transport;
        this.endpointUrl = required(endpointUrl, "MCP 服务地址不能为空");
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.enabled = enabled == null || enabled;
        this.preload = preload != null && preload;
        this.timeoutMillis = timeoutMillis == null || timeoutMillis <= 0 ? 15000 : Math.min(timeoutMillis, 120000);
        this.allowPrivateNetwork = allowPrivateNetwork == null || allowPrivateNetwork;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isPreload() {
        return preload != null && preload;
    }

    public Map<String, String> headersOrEmpty() {
        return headers == null ? Map.of() : Map.copyOf(headers);
    }

    public List<McpToolDefinition> toolsOrEmpty() {
        return tools == null ? List.of() : List.copyOf(tools);
    }

    public McpClientGateway.McpEndpoint endpoint() {
        return new McpClientGateway.McpEndpoint(code, endpointUrl, headersOrEmpty(),
                timeoutMillis == null ? 15000 : timeoutMillis, allowPrivateNetwork == null || allowPrivateNetwork);
    }

    public void markConnected(McpClientGateway.McpProbeResult result) {
        this.lastStatus = McpConnectionStatus.CONNECTED;
        this.lastError = null;
        this.lastConnectedAt = LocalDateTime.now();
        this.protocolVersion = result.protocolVersion();
        this.serverName = result.serverName();
        this.serverVersion = result.serverVersion();
        this.tools = result.tools();
    }

    public void markFailed(String message) {
        this.lastStatus = McpConnectionStatus.ERROR;
        this.lastError = message == null || message.isBlank() ? "连接失败" : message;
    }

    public void replaceTools(List<McpToolDefinition> definitions) {
        Map<String, McpToolDefinition> deduped = new LinkedHashMap<>();
        for (McpToolDefinition definition : definitions == null ? List.<McpToolDefinition>of() : definitions) {
            if (definition != null && !definition.name().isBlank()) {
                deduped.putIfAbsent(definition.name(), definition);
            }
        }
        this.tools = List.copyOf(deduped.values());
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static String code(String value) {
        String normalized = required(value, "MCP 服务编码不能为空").toLowerCase();
        if (!normalized.matches("[a-z][a-z0-9_-]{1,63}")) {
            throw new BizException("MCP 服务编码仅支持小写字母、数字、下划线和短横线，且以字母开头");
        }
        return normalized;
    }
}
