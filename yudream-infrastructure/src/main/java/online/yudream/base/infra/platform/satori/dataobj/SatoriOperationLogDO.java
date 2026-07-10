package online.yudream.base.infra.platform.satori.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformSatoriOperationLog")
public class SatoriOperationLogDO extends BaseDO {
    @Indexed
    private Long connectionId;
    private String level;
    private String category;
    private String action;
    private String detail;
    @Indexed
    private LocalDateTime occurredAt;
}
