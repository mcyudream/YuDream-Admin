package online.yudream.base.interfaces.platform.mcp.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class McpServerRes {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String transport;
    private String endpointUrl;
    private Map<String, String> headers;
    private Boolean enabled;
    private Boolean preload;
    private Integer timeoutMillis;
    private Boolean allowPrivateNetwork;
    private String lastStatus;
    private String lastError;
    private String lastConnectedAt;
    private String protocolVersion;
    private String serverName;
    private String serverVersion;
    private List<Tool> tools;
    private String moduleCode;
    private Boolean moduleEnabled;
    private String updateTime;

    @Data
    @Builder
    public static class Tool {
        private String name;
        private String title;
        private String description;
        private String risk;
        private Boolean requiresConfirmation;
        private String platformToolCode;
    }
}
