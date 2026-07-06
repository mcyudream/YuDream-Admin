package online.yudream.base.plugin.alipay.interfaces.request;

import java.math.BigDecimal;

public record AlipayRechargeCreateRequest(
        String userId,
        String assetCode,
        BigDecimal amount,
        BigDecimal walletAmount,
        String subject,
        String body,
        String productType
) {
}
