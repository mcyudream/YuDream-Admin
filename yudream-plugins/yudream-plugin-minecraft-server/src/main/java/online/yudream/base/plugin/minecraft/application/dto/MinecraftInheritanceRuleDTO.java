package online.yudream.base.plugin.minecraft.application.dto;

import java.math.BigDecimal;

public record MinecraftInheritanceRuleDTO(
        String assetPattern,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal inheritRate,
        String rangeLabel
) {
}
