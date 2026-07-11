package online.yudream.base.infra.system.security.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "sysExternalLoginProvider")
public class ExternalLoginProviderDO extends BaseDO {
    @Indexed(unique = true) private String code;
    private String name; private String protocol; private String appId; private String appKey;
    private String callbackUrl; private boolean enabled; private String supportedTypes;
}
