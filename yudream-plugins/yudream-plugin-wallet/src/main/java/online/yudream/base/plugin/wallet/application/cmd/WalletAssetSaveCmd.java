package online.yudream.base.plugin.wallet.application.cmd;

import java.math.BigDecimal;

public record WalletAssetSaveCmd(
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
