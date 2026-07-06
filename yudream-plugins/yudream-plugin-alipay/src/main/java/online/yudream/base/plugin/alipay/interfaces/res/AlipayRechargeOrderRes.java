package online.yudream.base.plugin.alipay.interfaces.res;

import java.math.BigDecimal;

public record AlipayRechargeOrderRes(
        String outTradeNo,
        String userId,
        String assetCode,
        BigDecimal amount,
        BigDecimal walletAmount,
        String subject,
        String body,
        String productType,
        String status,
        String tradeNo,
        String walletTransactionId,
        long createdAt,
        long updatedAt,
        long paidAt
) {
}
