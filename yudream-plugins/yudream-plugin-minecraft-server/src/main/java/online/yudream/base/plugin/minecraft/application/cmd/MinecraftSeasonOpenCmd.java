package online.yudream.base.plugin.minecraft.application.cmd;

import java.math.BigDecimal;
import java.util.List;

public record MinecraftSeasonOpenCmd(
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
