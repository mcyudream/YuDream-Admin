package online.yudream.base.plugin.spi.system.wallet;

import java.math.BigDecimal;

public record PluginWalletAsset(
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
