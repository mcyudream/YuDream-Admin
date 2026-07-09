package online.yudream.base.plugin.projectprogress.interfaces.res;

import java.util.List;

public record ProjectProgressProjectRes(
        String id,
        String name,
        String description,
        List<String> managerUserIds,
        List<String> memberUserIds,
        List<StatusRes> statuses,
        String defaultStatusCode,
        String doneStatusCode,
        String reworkStatusCode,
        int minCheckInIntervalMinutes,
        List<String> allowedCheckInTypes,
        MinecraftPolicyRes minecraftPolicy,
        boolean enabled,
        long createdAt,
        long updatedAt
) {
    public record StatusRes(String code, String label, boolean terminal, int sort) {
    }

    public record MinecraftPolicyRes(boolean enabled, String serverId, int requiredOnlineMinutes,
                                     boolean includeAfk, boolean autoCheckInEnabled) {
    }
}
