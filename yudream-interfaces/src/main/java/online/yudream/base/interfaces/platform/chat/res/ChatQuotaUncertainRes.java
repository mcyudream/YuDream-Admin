package online.yudream.base.interfaces.platform.chat.res;

import java.time.LocalDate;

public record ChatQuotaUncertainRes(String reservationId, String userId, LocalDate usageDate, long heldTokens, String runId, long knownTokens) { }
