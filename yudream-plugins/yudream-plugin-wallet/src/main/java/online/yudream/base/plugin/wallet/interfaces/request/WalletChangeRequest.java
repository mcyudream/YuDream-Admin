package online.yudream.base.plugin.wallet.interfaces.request;

import java.math.BigDecimal;

public record WalletChangeRequest(
        String userId,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark
) {
}
