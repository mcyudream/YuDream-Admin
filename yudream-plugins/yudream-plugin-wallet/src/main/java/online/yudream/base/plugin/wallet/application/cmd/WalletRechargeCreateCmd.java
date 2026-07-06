package online.yudream.base.plugin.wallet.application.cmd;

import java.math.BigDecimal;

public record WalletRechargeCreateCmd(
        String userId,
        String assetCode,
        String channelCode,
        BigDecimal payAmount,
        String productType,
        String remark
) {
}
