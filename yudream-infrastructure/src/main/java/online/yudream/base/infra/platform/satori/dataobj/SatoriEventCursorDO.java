package online.yudream.base.infra.platform.satori.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformSatoriEventCursor")
public class SatoriEventCursorDO extends BaseDO {
    @Indexed(unique = true)
    private Long connectionId;
    private String sequence;
}
