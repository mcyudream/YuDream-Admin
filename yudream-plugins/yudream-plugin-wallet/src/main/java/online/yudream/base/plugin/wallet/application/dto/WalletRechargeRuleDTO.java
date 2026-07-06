package online.yudream.base.plugin.wallet.application.dto;

import java.math.BigDecimal;

public record WalletRechargeRuleDTO(
        String assetCode,
        boolean enabled,
        BigDecimal ratio,
        BigDecimal minPayAmount,
        BigDecimal maxPayAmount
) {
}
