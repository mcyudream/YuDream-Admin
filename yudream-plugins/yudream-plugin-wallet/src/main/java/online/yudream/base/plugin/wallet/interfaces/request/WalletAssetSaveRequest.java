package online.yudream.base.plugin.wallet.interfaces.request;

import java.math.BigDecimal;

public record WalletAssetSaveRequest(
        String code,
        String name,
        String symbol,
        Integer scale,
        Boolean money,
        Boolean enabled,
        Boolean transferEnabled,
        BigDecimal minTransferAmount
) {
}
