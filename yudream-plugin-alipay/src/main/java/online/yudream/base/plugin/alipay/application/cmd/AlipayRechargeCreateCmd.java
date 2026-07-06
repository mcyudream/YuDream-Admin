package online.yudream.base.plugin.alipay.application.cmd;

import java.math.BigDecimal;

public record AlipayRechargeCreateCmd(
        String userId,
        String assetCode,
        BigDecimal amount,
        BigDecimal walletAmount,
        String subject,
        String body,
        String productType
) {
}
