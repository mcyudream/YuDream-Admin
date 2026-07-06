package online.yudream.base.plugin.wallet.application.cmd;

import java.math.BigDecimal;

public record WalletTransferCmd(
        String fromUserId,
        String toUserId,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark
) {
}
