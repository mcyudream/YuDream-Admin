package online.yudream.base.application.platform.chat.dto;

import java.time.LocalDate;

public record ChatQuotaDTO(
        String userId,
        LocalDate usageDate,
        long usedTokens,
        long limitTokens,
        long remainingTokens,
        long reservedTokens,
        long uncertainTokens,
        /** 管理员不限额：limit/remaining 不具展示意义，界面只显示已用量 */
        boolean unlimited
) {
    public ChatQuotaDTO(String userId, LocalDate date, long used, long limit, long remaining) {
        this(userId, date, used, limit, remaining, 0, 0, false);
    }
}
