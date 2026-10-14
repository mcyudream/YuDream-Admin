package online.yudream.base.domain.platform.chat.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.time.LocalDate;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserChatQuota extends BaseDomain {

    private Long userId;
    private LocalDate usageDate;
    private long usedTokens;
    private long limitTokens;
    private long reservedTokens;
    private long uncertainTokens;

    public static UserChatQuota of(Long userId, LocalDate usageDate, long limitTokens) {
        return UserChatQuota.builder()
                .userId(userId)
                .usageDate(usageDate)
                .usedTokens(0)
                .limitTokens(limitTokens)
                .build();
    }

    public long remaining() {
        if (usedTokens >= limitTokens) return 0;
        long remaining = limitTokens - Math.max(0, usedTokens);
        if (reservedTokens >= remaining) return 0;
        remaining -= Math.max(0, reservedTokens);
        return uncertainTokens >= remaining ? 0 : remaining - Math.max(0, uncertainTokens);
    }

    public boolean exceed(AiUsage usage) {
        return usage != null && usage.totalTokens() > remaining();
    }

    public void add(AiUsage usage) {
        if (usage != null) {
            this.usedTokens += usage.totalTokens();
        }
    }
}
