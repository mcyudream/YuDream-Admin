package online.yudream.base.domain.platform.integration.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;

import java.time.LocalDateTime;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HttpInvocationLog extends BaseDomain {

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
