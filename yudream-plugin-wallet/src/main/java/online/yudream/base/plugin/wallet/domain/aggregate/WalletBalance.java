package online.yudream.base.plugin.wallet.domain.aggregate;

import java.math.BigDecimal;

public record WalletBalance(
        String userId,
        String assetCode,
        BigDecimal balance,
        long updatedAt
) {

    public static WalletBalance empty(String userId, String assetCode, int scale) {
        return new WalletBalance(userId, assetCode, BigDecimal.ZERO.setScale(scale), System.currentTimeMillis());
    }

    public WalletBalance credit(BigDecimal amount) {
        return new WalletBalance(userId, assetCode, balance.add(amount), System.currentTimeMillis());
    }

    public WalletBalance debit(BigDecimal amount) {
        BigDecimal next = balance.subtract(amount);
        if (next.signum() < 0) {
            throw new IllegalArgumentException("余额不足");
        }
        return new WalletBalance(userId, assetCode, next, System.currentTimeMillis());
    }
}
