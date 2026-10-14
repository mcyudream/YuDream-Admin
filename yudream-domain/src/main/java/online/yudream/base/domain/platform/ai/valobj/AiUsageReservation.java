package online.yudream.base.domain.platform.ai.valobj;

import java.time.LocalDate;

public record AiUsageReservation(String id, Long userId, LocalDate usageDate, long reservedTokens,
                                  String runId, java.time.Instant leaseExpiresAt, boolean modelStarted, long knownTokens) {
    public AiUsageReservation(String id, Long userId, LocalDate usageDate, long reservedTokens,
                              String runId, java.time.Instant leaseExpiresAt, boolean modelStarted) {
        this(id, userId, usageDate, reservedTokens, runId, leaseExpiresAt, modelStarted, 0);
    }
    public AiUsageReservation(String id, Long userId, LocalDate usageDate, long reservedTokens) {
        this(id, userId, usageDate, reservedTokens, null, null, false, 0);
    }
    public AiUsageReservation(String id, Long userId, LocalDate usageDate, long reservedTokens, String runId, java.time.Instant expiresAt) {
        this(id, userId, usageDate, reservedTokens, runId, expiresAt, false, 0);
    }
}
