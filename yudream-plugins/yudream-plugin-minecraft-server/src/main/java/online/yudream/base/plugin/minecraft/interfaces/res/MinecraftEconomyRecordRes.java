package online.yudream.base.plugin.minecraft.interfaces.res;

import java.math.BigDecimal;

public record MinecraftEconomyRecordRes(
        String id,
        String type,
        String source,
        String status,
        String assetCode,
        BigDecimal amount,
        String businessNo,
        String remark,
        long createdAt
) {
}
