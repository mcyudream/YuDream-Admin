package online.yudream.base.plugin.wallet.interfaces.res;

import java.math.BigDecimal;

public record WalletBalanceRes(
        String userId,
        WalletUserRes user,
        String assetCode,
        BigDecimal balance,
        long updatedAt
) {
}
