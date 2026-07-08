package online.yudream.base.plugin.minecraft.interfaces.res;

import java.math.BigDecimal;

public record MinecraftSeasonAdjustmentRes(
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
