package online.yudream.base.infra.system.monitor.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "sysApiLog")
public class ApiLogDO extends BaseDO {

    private String method;
    private String path;
    private String query;
    private String requestBody;
    private Integer status;
    private Long costMs;
    private Boolean success;
    private Long loginId;
    private String ip;
    private String userAgent;
    private String errorMessage;
}
