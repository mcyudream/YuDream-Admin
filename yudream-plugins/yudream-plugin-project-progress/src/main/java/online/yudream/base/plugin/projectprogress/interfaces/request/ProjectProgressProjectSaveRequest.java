package online.yudream.base.plugin.projectprogress.interfaces.request;

import java.util.List;

public record ProjectProgressProjectSaveRequest(
        String name,
        String description,
        List<String> managerUserIds,
        List<String> memberUserIds,
        List<Status> statuses,
        String defaultStatusCode,
        String doneStatusCode,
        String reworkStatusCode,
        Integer minCheckInIntervalMinutes,
        List<String> allowedCheckInTypes,
        MinecraftPolicy minecraftPolicy,
        Boolean enabled
) {
    public record Status(String code, String label, Boolean terminal, Integer sort) {
    }

    public record MinecraftPolicy(Boolean enabled, String serverId, Integer requiredOnlineMinutes,
                                  Boolean includeAfk, Boolean autoCheckInEnabled) {
    }
}
