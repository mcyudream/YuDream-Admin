package online.yudream.base.plugin.minecraft.domain.valobj;

import java.math.BigDecimal;

public record MinecraftSeasonAdjustment(
        String userId,
        String assetCode,
        BigDecimal inheritedAmount,
        BigDecimal seasonIncomeAmount,
        BigDecimal seasonTotalAmount,
        BigDecimal nextInheritedAmount,
        BigDecimal walletBalanceBefore,
        BigDecimal deltaAmount,
        String direction,
        String ruleLabel,
        String walletTransactionId,
        String rollbackTransactionId
) {

    public MinecraftSeasonAdjustment withWalletTransaction(String transactionId) {
        return new MinecraftSeasonAdjustment(userId, assetCode, inheritedAmount, seasonIncomeAmount, seasonTotalAmount,
                nextInheritedAmount, walletBalanceBefore, deltaAmount, direction, ruleLabel, transactionId, rollbackTransactionId);
    }

    public MinecraftSeasonAdjustment withRollbackTransaction(String transactionId) {
        return new MinecraftSeasonAdjustment(userId, assetCode, inheritedAmount, seasonIncomeAmount, seasonTotalAmount,
                nextInheritedAmount, walletBalanceBefore, deltaAmount, direction, ruleLabel, walletTransactionId, transactionId);
    }
}
