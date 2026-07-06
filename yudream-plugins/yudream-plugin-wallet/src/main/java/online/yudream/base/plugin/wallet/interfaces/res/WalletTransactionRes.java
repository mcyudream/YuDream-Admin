package online.yudream.base.plugin.wallet.interfaces.res;

import java.math.BigDecimal;

public record WalletTransactionRes(
        String id,
        String businessNo,
        String type,
        String source,
        String assetCode,
        String fromUserId,
        WalletUserRes fromUser,
        String toUserId,
        WalletUserRes toUser,
        String direction,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        String remark,
        long createdAt
) {
}
