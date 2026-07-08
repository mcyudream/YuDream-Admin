package online.yudream.base.plugin.minecraft.application.dto;

import java.math.BigDecimal;

public record MinecraftEconomyRecordDTO(
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
