package online.yudream.base.infra.system.user.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysMessagingIdentity")
public class MessagingIdentityDO extends BaseDO {

    private Long userId;
    private String protocol;
    private Long connectionId;
    private String appId;
    private String identityType;
    private String identity;
    private String groupOpenid;
}
