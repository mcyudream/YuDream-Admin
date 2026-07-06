package online.yudream.base.plugin.wallet.application.cmd;

import java.math.BigDecimal;

public record WalletChangeCmd(
        String userId,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark
) {
}
