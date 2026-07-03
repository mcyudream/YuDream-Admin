package online.yudream.base.infra.platform.integration.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformHttpInvocationLog")
public class HttpInvocationLogDO extends BaseDO {
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
