package online.yudream.base.infra.platform.chat.dataobj;

import lombok.Data;
import java.time.Instant;

@Data
public class ChatQuotaReservationDO {
    private long tokens;
    private String runId;
    private Instant leaseExpiresAt;
    private boolean modelStarted;
    private long knownTokens;
    private long originalTokens;
}
