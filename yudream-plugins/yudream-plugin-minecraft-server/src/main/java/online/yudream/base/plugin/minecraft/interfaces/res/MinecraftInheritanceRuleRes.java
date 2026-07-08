package online.yudream.base.plugin.minecraft.interfaces.res;

import java.math.BigDecimal;

public record MinecraftInheritanceRuleRes(
        String assetPattern,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal inheritRate,
        String rangeLabel
) {
}
