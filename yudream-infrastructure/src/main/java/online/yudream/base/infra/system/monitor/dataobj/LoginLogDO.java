package online.yudream.base.infra.system.monitor.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "sysLoginLog")
public class LoginLogDO extends BaseDO {

    private String username;
    private Long userId;
    private Boolean success;
    private String message;
    private String ip;
    private String userAgent;
    private String token;
}
