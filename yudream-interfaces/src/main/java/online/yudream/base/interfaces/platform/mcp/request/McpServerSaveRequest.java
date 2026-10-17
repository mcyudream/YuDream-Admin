package online.yudream.base.interfaces.platform.mcp.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class McpServerSaveRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private String description;
    /** 传输类型，当前仅 STREAMABLE_HTTP。 */
    private String transport;
    @NotBlank
    private String endpointUrl;
    /** 请求头；值为空或等于掩码占位符时表示保留服务端已保存的凭据。 */
    private Map<String, String> headers;
    private Boolean enabled;
    /** 常驻模块：开启后工具无需 load_tools 加载。 */
    private Boolean preload;
    private Integer timeoutMillis;
    private Boolean allowPrivateNetwork;
}
