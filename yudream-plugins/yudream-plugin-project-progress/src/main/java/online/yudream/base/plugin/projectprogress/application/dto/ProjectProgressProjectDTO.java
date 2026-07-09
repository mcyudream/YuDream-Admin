package online.yudream.base.plugin.projectprogress.application.dto;

import java.util.List;

public record ProjectProgressProjectDTO(
        String id,
        String name,
        String description,
        List<String> managerUserIds,
        List<String> memberUserIds,
        List<StatusDTO> statuses,
        String defaultStatusCode,
        String doneStatusCode,
        String reworkStatusCode,
        int minCheckInIntervalMinutes,
        List<String> allowedCheckInTypes,
        MinecraftPolicyDTO minecraftPolicy,
        boolean enabled,
        long createdAt,
        long updatedAt
) {
    public record StatusDTO(String code, String label, boolean terminal, int sort) {
    }

    public record MinecraftPolicyDTO(boolean enabled, String serverId, int requiredOnlineMinutes,
                                     boolean includeAfk, boolean autoCheckInEnabled) {
    }
}
