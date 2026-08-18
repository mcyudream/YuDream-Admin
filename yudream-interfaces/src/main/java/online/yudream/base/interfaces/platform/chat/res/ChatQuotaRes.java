package online.yudream.base.interfaces.platform.chat.res;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ChatQuotaRes(
        String userId,
        LocalDate usageDate,
        long usedTokens,
        long limitTokens,
        long remainingTokens
) {
}
