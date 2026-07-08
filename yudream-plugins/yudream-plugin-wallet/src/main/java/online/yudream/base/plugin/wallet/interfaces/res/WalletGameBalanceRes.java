package online.yudream.base.plugin.wallet.interfaces.res;

import java.math.BigDecimal;

public record WalletGameBalanceRes(
        String userId,
        String playerName,
        String playerUuid,
        String assetCode,
        BigDecimal balance,
        long updatedAt
) {
}
