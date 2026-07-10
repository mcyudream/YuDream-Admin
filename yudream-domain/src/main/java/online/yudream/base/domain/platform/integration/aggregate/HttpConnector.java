package online.yudream.base.domain.platform.integration.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HttpConnector extends BaseDomain {

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

    public static HttpConnector create(String name, String code, String url, HttpMethodType method) {
        HttpConnector connector = new HttpConnector();
        connector.name = required(name, "连接器名称不能为空");
        connector.code = required(code, "连接器编码不能为空");
        connector.url = required(url, "请求地址不能为空");
        connector.method = method == null ? HttpMethodType.GET : method;
        connector.headers = new HashMap<>();
        connector.queryParams = new HashMap<>();
        connector.timeoutMillis = 10000;
        connector.retryTimes = 0;
        connector.status = ConnectorStatus.ACTIVE;
        return connector;
    }

    public void update(String name,
                       String url,
                       HttpMethodType method,
                       Map<String, String> headers,
                       Map<String, String> queryParams,
                       String bodyTemplate,
                       int timeoutMillis,
                       int retryTimes,
                       ConnectorStatus status) {
        this.name = required(name, "连接器名称不能为空");
        this.url = required(url, "请求地址不能为空");
        this.method = method == null ? HttpMethodType.GET : method;
        this.headers = new HashMap<>(headers == null ? Map.of() : headers);
        this.queryParams = new HashMap<>(queryParams == null ? Map.of() : queryParams);
        this.bodyTemplate = bodyTemplate;
        this.timeoutMillis = timeoutMillis <= 0 ? 10000 : timeoutMillis;
        this.retryTimes = Math.max(retryTimes, 0);
        this.status = status == null ? ConnectorStatus.ACTIVE : status;
    }

    public void disable() {
        this.status = ConnectorStatus.DISABLED;
    }

    public void activate() {
        this.status = ConnectorStatus.ACTIVE;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
