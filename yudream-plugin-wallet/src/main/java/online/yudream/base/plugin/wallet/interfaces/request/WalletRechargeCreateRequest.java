package online.yudream.base.plugin.wallet.interfaces.request;

import java.math.BigDecimal;

public record WalletRechargeCreateRequest(
        String userId,
        String assetCode,
        String channelCode,
        BigDecimal payAmount,
        String productType,
        String remark
) {
}
