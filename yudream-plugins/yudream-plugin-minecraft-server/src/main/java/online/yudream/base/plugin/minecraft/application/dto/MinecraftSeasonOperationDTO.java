package online.yudream.base.plugin.minecraft.application.dto;

import java.util.List;

public record MinecraftSeasonOperationDTO(
        String id,
        String serverId,
        String fromSeasonId,
        String toSeasonId,
        String toSeasonName,
        String status,
        List<MinecraftInheritanceRuleDTO> rules,
        List<MinecraftSeasonAdjustmentDTO> adjustments,
        String operatorUserId,
        String remark,
        long createdAt,
        Long rolledBackAt
) {
}
