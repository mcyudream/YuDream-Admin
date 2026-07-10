package online.yudream.base.domain.platform.satori.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

/** A durable acknowledgement point, advanced only after an event was published. */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SatoriEventCursor extends BaseDomain {
    private Long connectionId;
    private String sequence;

    public static SatoriEventCursor advance(Long connectionId, String sequence) {
        if (connectionId == null || sequence == null || sequence.isBlank()) {
            throw new BizException("Satori 事件游标不能为空");
        }
        return SatoriEventCursor.builder().connectionId(connectionId).sequence(sequence.trim()).build();
    }

    public void advanceTo(String sequence) {
        if (sequence == null || sequence.isBlank()) {
            throw new BizException("Satori 事件游标不能为空");
        }
        this.sequence = sequence.trim();
    }
}
