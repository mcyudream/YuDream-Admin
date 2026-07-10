package online.yudream.base.infra.platform.satori.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformSatoriLogin")
@CompoundIndex(name = "satori_login_unique", def = "{'connectionId': 1, 'platform': 1, 'userId': 1}", unique = true)
public class SatoriLoginDO extends BaseDO {
    private Long connectionId;
    private String platform;
    private String userId;
    private SatoriLoginStatus status;
    private String adapter;
    private List<String> features;
}
