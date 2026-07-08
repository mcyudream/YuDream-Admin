package online.yudream.base.plugin.wallet.interfaces.res;

import java.math.BigDecimal;

public record WalletGameTransactionRes(
        String id,
        String businessNo,
        String type,
        String source,
        String userId,
        String playerName,
        String playerUuid,
        String assetCode,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String remark,
        long createdAt
) {
}
