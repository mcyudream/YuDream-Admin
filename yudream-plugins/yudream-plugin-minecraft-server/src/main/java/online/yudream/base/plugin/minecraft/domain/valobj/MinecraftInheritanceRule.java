package online.yudream.base.plugin.minecraft.domain.valobj;

import java.math.BigDecimal;
import java.util.Locale;

public record MinecraftInheritanceRule(
        String assetPattern,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal inheritRate
) {

    public MinecraftInheritanceRule {
        assetPattern = assetPattern == null || assetPattern.isBlank() ? "*" : assetPattern.trim().toUpperCase(Locale.ROOT);
        if (!assetPattern.matches("[A-Z0-9_*]{1,32}")) {
            throw new IllegalArgumentException("币种通配只能包含大写字母、数字、下划线和 *：" + assetPattern);
        }
        minAmount = normalizeAmount(minAmount, BigDecimal.ZERO);
        if (minAmount.signum() < 0) {
            throw new IllegalArgumentException("继承区间最小值不能小于 0：" + assetPattern);
        }
        maxAmount = normalizeAmount(maxAmount, null);
        if (maxAmount != null && maxAmount.compareTo(minAmount) <= 0) {
            throw new IllegalArgumentException("继承区间最大值必须大于最小值：" + assetPattern + " " + minAmount + "-" + maxAmount);
        }
        inheritRate = normalizeAmount(inheritRate, null);
        if (inheritRate == null || inheritRate.compareTo(BigDecimal.ZERO) < 0 || inheritRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("继承比例必须在 0 到 1 之间：" + assetPattern);
        }
    }

    public boolean matchesAsset(String assetCode) {
        String code = assetCode == null ? "" : assetCode.trim().toUpperCase(Locale.ROOT);
        if ("*".equals(assetPattern)) {
            return true;
        }
        if (!assetPattern.contains("*")) {
            return assetPattern.equals(code);
        }
        String regex = assetPattern.replace("*", ".*");
        return code.matches(regex);
    }

    public boolean matchesAmount(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.compareTo(minAmount) >= 0 && (maxAmount == null || value.compareTo(maxAmount) < 0);
    }

    public String rangeLabel() {
        if (maxAmount == null) {
            return ">=" + minAmount.stripTrailingZeros().toPlainString();
        }
        return minAmount.stripTrailingZeros().toPlainString() + "-" + maxAmount.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal normalizeAmount(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value.stripTrailingZeros();
    }
}
