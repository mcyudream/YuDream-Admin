package online.yudream.base.application.platform.chat.dto;

import java.time.LocalDate;

public record ChatQuotaUncertainDTO(String reservationId, String userId, LocalDate usageDate, long heldTokens, String runId, long knownTokens) { }
