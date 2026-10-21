package online.yudream.base.interfaces.platform.chat.res;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ChatQuotaRes(
        String userId,
        LocalDate usageDate,
        long usedTokens,
        long limitTokens,
        long remainingTokens,
        long reservedTokens,
        long uncertainTokens,
        /** 管理员不限额：界面只显示已用量 */
        boolean unlimited
) {
}
