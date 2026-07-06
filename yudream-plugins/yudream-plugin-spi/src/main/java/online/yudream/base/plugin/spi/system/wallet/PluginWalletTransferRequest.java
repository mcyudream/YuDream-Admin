package online.yudream.base.plugin.spi.system.wallet;

import java.math.BigDecimal;

public record PluginWalletTransferRequest(
        String fromUserId,
        String toUserId,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark
) {
}
