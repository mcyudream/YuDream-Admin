package online.yudream.base.plugin.minecraft.application.dto;

import java.math.BigDecimal;

public record MinecraftSeasonAdjustmentDTO(
        String userId,
        String assetCode,
        BigDecimal inheritedAmount,
        BigDecimal seasonIncomeAmount,
        BigDecimal seasonTotalAmount,
        BigDecimal realTotalIncomeAmount,
        BigDecimal nextInheritedAmount,
        BigDecimal walletBalanceBefore,
        BigDecimal deltaAmount,
        String direction,
        String ruleLabel,
        String walletTransactionId,
        String rollbackTransactionId
) {
}
