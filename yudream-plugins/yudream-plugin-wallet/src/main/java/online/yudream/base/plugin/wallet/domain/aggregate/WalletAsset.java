package online.yudream.base.plugin.wallet.domain.aggregate;

import java.math.BigDecimal;
import java.util.Locale;

public record WalletAsset(
        String code,
        String name,
        String symbol,
        int scale,
        boolean money,
        boolean enabled,
        boolean transferEnabled,
        BigDecimal minTransferAmount,
        long createdAt,
        long updatedAt
) {

    public static WalletAsset create(String code, String name, String symbol, int scale, boolean money, BigDecimal minTransferAmount) {
        long now = System.currentTimeMillis();
        return new WalletAsset(normalizeCode(code), requireText(name, "资产名称不能为空"), symbol, scale, money, true, true,
                normalizeAmount(minTransferAmount), now, now).withValidatedScale();
    }

    public WalletAsset update(String name, String symbol, Integer scale, Boolean enabled, Boolean transferEnabled, BigDecimal minTransferAmount) {
        int nextScale = scale == null ? this.scale : scale;
        return new WalletAsset(
                code,
                requireText(name == null ? this.name : name, "资产名称不能为空"),
                symbol == null ? this.symbol : symbol,
                validateScale(nextScale),
                money,
                enabled == null ? this.enabled : enabled,
                transferEnabled == null ? this.transferEnabled : transferEnabled,
                normalizeAmount(minTransferAmount == null ? this.minTransferAmount : minTransferAmount),
                createdAt,
                System.currentTimeMillis()
        );
    }

    public WalletAsset requireEnabled() {
        if (!enabled) {
            throw new IllegalArgumentException("资产已停用：" + code);
        }
        return this;
    }

    public WalletAsset requireTransferEnabled() {
        requireEnabled();
        if (!transferEnabled) {
            throw new IllegalArgumentException("该币种已关闭转账：" + name);
        }
        return this;
    }

    private WalletAsset withValidatedScale() {
        validateScale(scale);
        return this;
    }

    public BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > scale) {
            throw new IllegalArgumentException("金额精度不能超过 " + scale + " 位小数");
        }
        return amount.setScale(scale);
    }

    public void validateTransferAmount(BigDecimal amount) {
        BigDecimal normalized = normalize(amount);
        if (minTransferAmount != null && normalized.compareTo(minTransferAmount.setScale(scale)) < 0) {
            throw new IllegalArgumentException("转账金额不能小于 " + minTransferAmount);
        }
    }

    public static String normalizeCode(String code) {
        String value = requireText(code, "资产编码不能为空").trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_]{2,32}")) {
            throw new IllegalArgumentException("资产编码只能包含大写字母、数字和下划线，长度 2-32");
        }
        return value;
    }

    private static int validateScale(int scale) {
        if (scale < 0 || scale > 8) {
            throw new IllegalArgumentException("资产精度必须在 0 到 8 之间");
        }
        return scale;
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.stripTrailingZeros();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
