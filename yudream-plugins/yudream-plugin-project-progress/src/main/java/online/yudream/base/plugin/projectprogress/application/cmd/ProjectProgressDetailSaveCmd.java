package online.yudream.base.plugin.projectprogress.application.cmd;

import java.util.List;

public record ProjectProgressDetailSaveCmd(
        String title,
        String description,
        String statusCode,
        String assignmentMode,
        Integer requiredAssigneeCount,
        List<String> candidateUserIds,
        List<String> assigneeUserIds,
        List<String> acceptorUserIds,
        Boolean published,
        Long dueAt
) {
}
