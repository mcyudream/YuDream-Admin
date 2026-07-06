package online.yudream.base.plugin.spi.system.payment;

import java.math.BigDecimal;

public record PluginPaymentCreateResult(
        String channelCode,
        String channelName,
        String outTradeNo,
        String assetCode,
        BigDecimal payAmount,
        BigDecimal walletAmount,
        String productType,
        String status,
        String payloadType,
        String payPayload,
        long createdAt
) {
}
