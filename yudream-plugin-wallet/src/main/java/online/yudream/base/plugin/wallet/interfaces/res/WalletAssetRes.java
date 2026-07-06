package online.yudream.base.plugin.wallet.interfaces.res;

import java.math.BigDecimal;

public record WalletAssetRes(
        String code,
        String name,
        String symbol,
        int scale,
        boolean money,
        boolean enabled,
        boolean transferEnabled,
        BigDecimal minTransferAmount
) {
}
