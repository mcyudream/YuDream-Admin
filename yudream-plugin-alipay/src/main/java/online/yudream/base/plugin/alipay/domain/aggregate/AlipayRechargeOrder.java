package online.yudream.base.plugin.alipay.domain.aggregate;

import online.yudream.base.plugin.alipay.domain.enumerate.AlipayOrderStatus;
import online.yudream.base.plugin.alipay.domain.enumerate.AlipayProductType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record AlipayRechargeOrder(
        String outTradeNo,
        String userId,
        String assetCode,
        BigDecimal amount,
        BigDecimal walletAmount,
        String subject,
        String body,
        AlipayProductType productType,
        AlipayOrderStatus status,
        String tradeNo,
        String orderString,
        String walletTransactionId,
        long createdAt,
        long updatedAt,
        long paidAt
) {

    public static AlipayRechargeOrder create(String userId, String assetCode, BigDecimal amount, BigDecimal walletAmount,
                                             String subject, String body, AlipayProductType productType) {
        long now = System.currentTimeMillis();
        BigDecimal payAmount = requireAmount(amount, "支付金额必须大于 0").setScale(2);
        return new AlipayRechargeOrder(
                nextTradeNo(),
                requireText(userId, "用户不能为空"),
                requireText(assetCode, "充值资产不能为空").toUpperCase(),
                payAmount,
                requireAmount(walletAmount == null ? payAmount : walletAmount, "到账金额必须大于 0"),
                hasText(subject) ? subject.trim() : "钱包充值",
                trimToNull(body),
                productType == null ? AlipayProductType.APP : productType,
                AlipayOrderStatus.CREATED,
                null,
                null,
                null,
                now,
                now,
                0
        );
    }

    public AlipayRechargeOrder withOrderString(String orderString) {
        return new AlipayRechargeOrder(outTradeNo, userId, assetCode, amount, walletAmount, subject, body, productType,
                AlipayOrderStatus.PAYING, tradeNo, orderString, walletTransactionId, createdAt,
                System.currentTimeMillis(), paidAt);
    }

    public AlipayRechargeOrder markPaid(String tradeNo, String walletTransactionId) {
        if (status == AlipayOrderStatus.PAID) {
            return this;
        }
        long now = System.currentTimeMillis();
        return new AlipayRechargeOrder(outTradeNo, userId, assetCode, amount, walletAmount, subject, body, productType,
                AlipayOrderStatus.PAID, trimToNull(tradeNo), orderString, trimToNull(walletTransactionId),
                createdAt, now, now);
    }

    private static String nextTradeNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "YD" + date + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static BigDecimal requireAmount(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return amount;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
