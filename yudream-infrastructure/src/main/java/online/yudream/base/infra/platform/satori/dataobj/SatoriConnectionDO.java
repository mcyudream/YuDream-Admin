package online.yudream.base.infra.platform.satori.dataobj;

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
@Document(collection = "platformSatoriConnection")
public class SatoriConnectionDO extends BaseDO {
    private String name;
    private String baseUrl;
    private String platform;
    private String userId;
    private String encryptedToken;
    private boolean enabled;
}
