package online.yudream.base.plugin.wallet.interfaces.request;

import java.math.BigDecimal;

public record WalletGamePlayerRequest(
        String playerName,
        String playerUuid,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark,
        String matchMode
) {
}
