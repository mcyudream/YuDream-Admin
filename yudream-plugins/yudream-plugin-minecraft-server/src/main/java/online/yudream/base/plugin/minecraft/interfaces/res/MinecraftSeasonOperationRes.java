package online.yudream.base.plugin.minecraft.interfaces.res;

import java.util.List;

public record MinecraftSeasonOperationRes(
        String id,
        String serverId,
        String fromSeasonId,
        String toSeasonId,
        String toSeasonName,
        String status,
        List<MinecraftInheritanceRuleRes> rules,
        List<MinecraftSeasonAdjustmentRes> adjustments,
        String operatorUserId,
        String remark,
        long createdAt,
        Long rolledBackAt
) {
}
