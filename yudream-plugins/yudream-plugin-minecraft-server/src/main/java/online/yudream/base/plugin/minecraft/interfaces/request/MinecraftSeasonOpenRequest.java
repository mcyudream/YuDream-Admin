package online.yudream.base.plugin.minecraft.interfaces.request;

import java.math.BigDecimal;
import java.util.List;

public record MinecraftSeasonOpenRequest(
        String name,
        String description,
        Long startedAt,
        String remark,
        List<Rule> rules
) {

    public record Rule(
            String assetPattern,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal inheritRate
    ) {
    }
}
