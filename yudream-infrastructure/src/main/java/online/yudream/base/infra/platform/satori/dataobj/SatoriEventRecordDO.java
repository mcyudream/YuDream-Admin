package online.yudream.base.infra.platform.satori.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformSatoriEventRecord")
@CompoundIndex(name = "satori_event_idempotency", def = "{'connectionId': 1, 'sequence': 1}", unique = true)
public class SatoriEventRecordDO extends BaseDO {
    private Long connectionId;
    private String sequence;
    private String type;
    private String rawData;
    private LocalDateTime receivedAt;
    private LocalDateTime publishedAt;
    @Indexed(expireAfter = "0s")
    private LocalDateTime expireAt;
}
