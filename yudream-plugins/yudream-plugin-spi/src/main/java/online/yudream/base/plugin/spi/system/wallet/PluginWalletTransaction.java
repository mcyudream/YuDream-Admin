package online.yudream.base.plugin.spi.system.wallet;

import java.math.BigDecimal;

public record PluginWalletTransaction(
        String id,
        String businessNo,
        String type,
        String source,
        String assetCode,
        String fromUserId,
        String toUserId,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        String remark,
        long createdAt
) {
}
