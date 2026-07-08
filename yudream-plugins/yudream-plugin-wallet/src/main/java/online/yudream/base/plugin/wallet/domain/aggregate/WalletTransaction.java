package online.yudream.base.plugin.wallet.domain.aggregate;

import online.yudream.base.plugin.wallet.domain.enumerate.WalletTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletTransaction(
        String id,
        String businessNo,
        WalletTransactionType type,
        String source,
        String assetCode,
        String fromUserId,
        String toUserId,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        String remark,
        long createdAt
) {

    public static WalletTransaction credit(String businessNo, String assetCode, String toUserId, BigDecimal amount,
                                           BigDecimal toBalanceAfter, String remark) {
        return new WalletTransaction(nextId(), businessNo, WalletTransactionType.CREDIT, sourceOf(businessNo, "ADMIN"), assetCode, null, toUserId, amount,
                null, toBalanceAfter, remark, System.currentTimeMillis());
    }

    public static WalletTransaction debit(String businessNo, String assetCode, String fromUserId, BigDecimal amount,
                                          BigDecimal fromBalanceAfter, String remark) {
        return new WalletTransaction(nextId(), businessNo, WalletTransactionType.DEBIT, sourceOf(businessNo, "ADMIN"), assetCode, fromUserId, null, amount,
                fromBalanceAfter, null, remark, System.currentTimeMillis());
    }

    public static WalletTransaction transfer(String businessNo, String assetCode, String fromUserId, String toUserId,
                                             BigDecimal amount, BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter,
                                             String remark) {
        return new WalletTransaction(nextId(), businessNo, WalletTransactionType.TRANSFER, "TRANSFER", assetCode, fromUserId, toUserId,
                amount, fromBalanceAfter, toBalanceAfter, remark, System.currentTimeMillis());
    }

    private static String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String sourceOf(String businessNo, String fallback) {
        if (businessNo == null || businessNo.isBlank()) {
            return fallback;
        }
        String value = businessNo.trim().toLowerCase();
        if (value.startsWith("alipay:")) {
            return "ALIPAY";
        }
        if (value.startsWith("wallet-admin-")) {
            return "ADMIN";
        }
        if (value.startsWith("mc-season:") || value.startsWith("mc-season-rollback:") || value.startsWith("mc-season-partial-rollback:")) {
            return "MINECRAFT_SEASON";
        }
        return fallback;
    }
}
