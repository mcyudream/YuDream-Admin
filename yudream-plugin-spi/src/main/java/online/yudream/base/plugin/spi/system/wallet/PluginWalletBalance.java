package online.yudream.base.plugin.spi.system.wallet;

import java.math.BigDecimal;

public record PluginWalletBalance(
        String userId,
        String assetCode,
        BigDecimal balance,
        long updatedAt
) {
}
