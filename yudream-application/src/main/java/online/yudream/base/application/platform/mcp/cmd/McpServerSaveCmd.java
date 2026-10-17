package online.yudream.base.application.platform.mcp.cmd;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class McpServerSaveCmd {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String transport;
    private String endpointUrl;
    /** 请求头；值为空或等于掩码占位符时保留原值。 */
    private Map<String, String> headers;
    private Boolean enabled;
    private Boolean preload;
    private Integer timeoutMillis;
    private Boolean allowPrivateNetwork;
}
