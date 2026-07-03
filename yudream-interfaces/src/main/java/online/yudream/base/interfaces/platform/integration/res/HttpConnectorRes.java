package online.yudream.base.interfaces.platform.integration.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class HttpConnectorRes {
    private Long id;
    private String name;
    private String code;
    private String url;
    private HttpMethodType method;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String bodyTemplate;
    private int timeoutMillis;
    private int retryTimes;
    private ConnectorStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
