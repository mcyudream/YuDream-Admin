package online.yudream.base.application.platform.chat.dto;

import java.time.LocalDate;

public record ChatQuotaDTO(
        String userId,
        LocalDate usageDate,
        long usedTokens,
        long limitTokens,
        long remainingTokens
) {
}
