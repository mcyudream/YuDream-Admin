package online.yudream.base.plugin.projectprogress.domain.aggregate;

import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectCheckInType;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectFileEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectLocationEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftEvidence;

import java.util.List;
import java.util.UUID;

public record ProjectCheckInRecord(
        String id,
        String projectId,
        String detailId,
        String userId,
        ProjectCheckInType type,
        String summary,
        List<ProjectFileEvidence> files,
        ProjectLocationEvidence location,
        ProjectMinecraftEvidence minecraft,
        long createdAt
) {

    public ProjectCheckInRecord {
        id = requireText(id, "打卡记录 ID 不能为空");
        projectId = requireText(projectId, "项目 ID 不能为空");
        detailId = requireText(detailId, "工作细节 ID 不能为空");
        userId = requireText(userId, "打卡用户不能为空");
        if (type == null) {
            throw new IllegalArgumentException("打卡类型不能为空");
        }
        summary = summary == null ? "" : summary.trim();
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static ProjectCheckInRecord create(String projectId, String detailId, String userId, ProjectCheckInType type,
                                              String summary, List<ProjectFileEvidence> files,
                                              ProjectLocationEvidence location, ProjectMinecraftEvidence minecraft) {
        return new ProjectCheckInRecord(UUID.randomUUID().toString(), projectId, detailId, userId, type, summary,
                files, location, minecraft, System.currentTimeMillis());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
