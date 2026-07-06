package online.yudream.base.plugin.wallet.application.dto;

import java.math.BigDecimal;

public record WalletRechargeCreateDTO(
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
