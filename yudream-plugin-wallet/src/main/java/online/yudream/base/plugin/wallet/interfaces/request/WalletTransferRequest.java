package online.yudream.base.plugin.wallet.interfaces.request;

import java.math.BigDecimal;

public record WalletTransferRequest(
        String fromUserId,
        String toUserId,
        String toAccount,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark
) {
}
