package online.yudream.base.infra.system.security.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "sysExternalAccount")
@CompoundIndex(name = "external_identity_unique", def = "{'providerCode': 1, 'platformType': 1, 'socialUid': 1}", unique = true)
public class ExternalAccountDO extends BaseDO {
    private Long userId; private String providerCode; private String platformType; private String socialUid;
    private String nickname; private String avatarUrl; private String gender; private String location;
}
