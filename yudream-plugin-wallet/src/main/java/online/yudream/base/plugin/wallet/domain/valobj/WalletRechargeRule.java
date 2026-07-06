package online.yudream.base.plugin.wallet.domain.valobj;

import java.math.BigDecimal;

public record WalletRechargeRule(
        String assetCode,
        boolean enabled,
        BigDecimal ratio,
        BigDecimal minPayAmount,
        BigDecimal maxPayAmount
) {

    public static WalletRechargeRule defaults(String assetCode) {
        return new WalletRechargeRule(assetCode, true, BigDecimal.ONE, new BigDecimal("1.00"), null).normalized();
    }

    public WalletRechargeRule normalized() {
        if (assetCode == null || assetCode.isBlank()) {
            throw new IllegalArgumentException("充值币种不能为空");
        }
        BigDecimal nextRatio = ratio == null ? BigDecimal.ONE : ratio.stripTrailingZeros();
        if (nextRatio.signum() <= 0) {
            throw new IllegalArgumentException("充值比例必须大于 0");
        }
        BigDecimal nextMin = minPayAmount == null ? BigDecimal.ZERO : minPayAmount.stripTrailingZeros();
        if (nextMin.signum() < 0) {
            throw new IllegalArgumentException("最低支付金额不能小于 0");
        }
        BigDecimal nextMax = maxPayAmount == null || maxPayAmount.signum() <= 0 ? null : maxPayAmount.stripTrailingZeros();
        if (nextMax != null && nextMax.compareTo(nextMin) < 0) {
            throw new IllegalArgumentException("最高支付金额不能小于最低支付金额");
        }
        return new WalletRechargeRule(assetCode.trim().toUpperCase(), enabled, nextRatio, nextMin, nextMax);
    }

    public void validatePayAmount(BigDecimal payAmount) {
        if (payAmount == null || payAmount.signum() <= 0) {
            throw new IllegalArgumentException("支付金额必须大于 0");
        }
        if (!enabled) {
            throw new IllegalArgumentException("该币种未开启充值");
        }
        if (payAmount.compareTo(minPayAmount) < 0) {
            throw new IllegalArgumentException("支付金额不能小于 " + minPayAmount);
        }
        if (maxPayAmount != null && payAmount.compareTo(maxPayAmount) > 0) {
            throw new IllegalArgumentException("支付金额不能大于 " + maxPayAmount);
        }
    }
}
