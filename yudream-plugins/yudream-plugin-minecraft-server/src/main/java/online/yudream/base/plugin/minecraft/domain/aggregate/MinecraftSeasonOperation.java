package online.yudream.base.plugin.minecraft.domain.aggregate;

import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftSeasonOperationStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftInheritanceRule;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftSeasonAdjustment;

import java.util.List;
import java.util.UUID;

public record MinecraftSeasonOperation(
        String id,
        String serverId,
        String fromSeasonId,
        String toSeasonId,
        String toSeasonName,
        MinecraftSeasonOperationStatus status,
        List<MinecraftInheritanceRule> rules,
        List<MinecraftSeasonAdjustment> adjustments,
        String operatorUserId,
        String remark,
        long createdAt,
        Long rolledBackAt
) {

    public MinecraftSeasonOperation {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        rules = rules == null ? List.of() : List.copyOf(rules);
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
        status = status == null ? MinecraftSeasonOperationStatus.PREVIEW : status;
    }

    public static MinecraftSeasonOperation preview(String serverId, String fromSeasonId, String toSeasonId, String toSeasonName,
                                                   List<MinecraftInheritanceRule> rules,
                                                   List<MinecraftSeasonAdjustment> adjustments,
                                                   String operatorUserId,
                                                   String remark) {
        return new MinecraftSeasonOperation(null, serverId, fromSeasonId, toSeasonId, toSeasonName,
                MinecraftSeasonOperationStatus.PREVIEW, rules, adjustments, operatorUserId, remark,
                System.currentTimeMillis(), null);
    }

    public MinecraftSeasonOperation applied(List<MinecraftSeasonAdjustment> appliedAdjustments) {
        return new MinecraftSeasonOperation(id, serverId, fromSeasonId, toSeasonId, toSeasonName,
                MinecraftSeasonOperationStatus.APPLIED, rules, appliedAdjustments, operatorUserId, remark, createdAt, null);
    }

    public MinecraftSeasonOperation rolledBack(List<MinecraftSeasonAdjustment> rolledBackAdjustments) {
        return new MinecraftSeasonOperation(id, serverId, fromSeasonId, toSeasonId, toSeasonName,
                MinecraftSeasonOperationStatus.ROLLED_BACK, rules, rolledBackAdjustments, operatorUserId, remark,
                createdAt, System.currentTimeMillis());
    }
}
