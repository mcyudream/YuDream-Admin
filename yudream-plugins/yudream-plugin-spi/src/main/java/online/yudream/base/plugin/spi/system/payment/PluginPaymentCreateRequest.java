package online.yudream.base.plugin.spi.system.payment;

import java.math.BigDecimal;

public record PluginPaymentCreateRequest(
        String userId,
        String assetCode,
        BigDecimal payAmount,
        BigDecimal walletAmount,
        String subject,
        String body,
        String productType
) {
}
