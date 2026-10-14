package online.yudream.base.application.platform.chat.dto;

import java.time.LocalDate;

public record ChatQuotaDTO(
        String userId,
        LocalDate usageDate,
        long usedTokens,
        long limitTokens,
        long remainingTokens,
        long reservedTokens,
        long uncertainTokens
) {
    public ChatQuotaDTO(String userId, LocalDate date, long used, long limit, long remaining) {
        this(userId, date, used, limit, remaining, 0, 0);
    }
}
