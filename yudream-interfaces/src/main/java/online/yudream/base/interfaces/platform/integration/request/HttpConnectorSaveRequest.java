package online.yudream.base.interfaces.platform.integration.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;

import java.util.HashMap;
import java.util.Map;

@Data
public class HttpConnectorSaveRequest {
    @NotBlank(message = "连接器名称不能为空")
    private String name;
    @NotBlank(message = "连接器编码不能为空")
    private String code;
    @NotBlank(message = "请求地址不能为空")
    private String url;
    private HttpMethodType method;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private String bodyTemplate;
    private int timeoutMillis = 10000;
    private int retryTimes;
    private ConnectorStatus status;
}
