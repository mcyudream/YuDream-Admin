package online.yudream.base.infra.platform.mcp.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.mcp.enumerate.McpConnectionStatus;
import online.yudream.base.domain.platform.mcp.enumerate.McpTransport;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformMcpServer")
public class McpServerDO extends BaseDO {
    @Indexed(unique = true)
    private String code;
    private String name;
    private String description;
    private McpTransport transport;
    private String endpointUrl;
    private Map<String, String> headers;
    private Boolean enabled;
    private Boolean preload;
    private Integer timeoutMillis;
    private Boolean allowPrivateNetwork;
    private McpConnectionStatus lastStatus;
    private String lastError;
    private LocalDateTime lastConnectedAt;
    private String protocolVersion;
    private String serverName;
    private String serverVersion;
    /** tools/list 快照（JSON 数组字符串，避免 record 直接落库的映射差异）。 */
    private String toolsJson;
}
