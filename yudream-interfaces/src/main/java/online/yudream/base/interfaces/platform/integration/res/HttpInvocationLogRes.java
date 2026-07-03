package online.yudream.base.interfaces.platform.integration.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class HttpInvocationLogRes {
    private Long id;
    private Long connectorId;
    private String connectorCode;
    private String url;
    private HttpMethodType method;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private int responseStatus;
    private String responseBody;
    private long durationMillis;
    private ExecutionStatus status;
    private String errorMessage;
    private LocalDateTime invokedAt;
}
