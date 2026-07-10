package online.yudream.base.domain.platform.satori.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SatoriEventRecord extends BaseDomain {

    private Long connectionId;
    private String sequence;
    private String type;
    private String rawData;
    private LocalDateTime receivedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime expireAt;

    public static SatoriEventRecord create(Long connectionId, String sequence, String type, String rawData, int retentionDays) {
        if (connectionId == null || sequence == null || sequence.isBlank()) {
            throw new BizException("Satori 事件游标不能为空");
        }
        LocalDateTime receivedAt = LocalDateTime.now();
        return SatoriEventRecord.builder()
                .connectionId(connectionId)
                .sequence(sequence.trim())
                .type(type == null ? null : type.trim())
                .rawData(rawData)
                .receivedAt(receivedAt)
                .expireAt(receivedAt.plusDays(Math.max(retentionDays, 1)))
                .build();
    }

    public String idempotencyKey() {
        return connectionId + ":" + sequence;
    }

    public boolean published() {
        return publishedAt != null;
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }
}
