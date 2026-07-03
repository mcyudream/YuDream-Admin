package online.yudream.base.application.platform.integration.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class HttpConnectorSaveCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private String url;
    private HttpMethodType method;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private String bodyTemplate;
    private int timeoutMillis;
    private int retryTimes;
    private ConnectorStatus status;
}
