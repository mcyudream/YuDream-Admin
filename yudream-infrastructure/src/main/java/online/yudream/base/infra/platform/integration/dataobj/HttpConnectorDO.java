package online.yudream.base.infra.platform.integration.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformHttpConnector")
public class HttpConnectorDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private String url;
    private HttpMethodType method;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String bodyTemplate;
    private int timeoutMillis;
    private int retryTimes;
    private ConnectorStatus status;
}
