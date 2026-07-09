package online.yudream.base.plugin.projectprogress.domain.aggregate;

import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectAssignmentMode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record ProjectWorkDetail(
        String id,
        String projectId,
        String title,
        String description,
        String statusCode,
        ProjectAssignmentMode assignmentMode,
        int requiredAssigneeCount,
        List<String> candidateUserIds,
        List<String> assigneeUserIds,
        List<String> acceptorUserIds,
        boolean published,
        Long dueAt,
        long createdAt,
        long updatedAt
) {

    public ProjectWorkDetail {
        id = requireText(id, "工作细节 ID 不能为空");
        projectId = requireText(projectId, "项目 ID 不能为空");
        title = requireText(title, "工作细节标题不能为空");
        description = text(description);
        statusCode = requireText(statusCode, "状态不能为空").toUpperCase();
        assignmentMode = assignmentMode == null ? ProjectAssignmentMode.CLAIM : assignmentMode;
        requiredAssigneeCount = Math.max(requiredAssigneeCount, 1);
        candidateUserIds = normalizeIds(candidateUserIds);
        assigneeUserIds = normalizeIds(assigneeUserIds);
        acceptorUserIds = normalizeIds(acceptorUserIds);
    }

    public static ProjectWorkDetail create(String projectId, String title, String description, String statusCode,
                                           ProjectAssignmentMode assignmentMode, int requiredAssigneeCount,
                                           List<String> candidateUserIds, List<String> assigneeUserIds,
                                           List<String> acceptorUserIds, Long dueAt) {
        long now = System.currentTimeMillis();
        return new ProjectWorkDetail(UUID.randomUUID().toString(), projectId, title, description, statusCode,
                assignmentMode, requiredAssigneeCount, candidateUserIds, assigneeUserIds, acceptorUserIds,
                false, dueAt, now, now);
    }

    public ProjectWorkDetail update(String title, String description, String statusCode, ProjectAssignmentMode assignmentMode,
                                    int requiredAssigneeCount, List<String> candidateUserIds, List<String> assigneeUserIds,
                                    List<String> acceptorUserIds, Boolean published, Long dueAt) {
        return new ProjectWorkDetail(id, projectId, title, description, statusCode, assignmentMode, requiredAssigneeCount,
                candidateUserIds, assigneeUserIds, acceptorUserIds, published == null ? this.published : published,
                dueAt, createdAt, System.currentTimeMillis());
    }

    public ProjectWorkDetail publish(List<String> assignees) {
        return new ProjectWorkDetail(id, projectId, title, description, statusCode, assignmentMode, requiredAssigneeCount,
                candidateUserIds, assignees == null ? assigneeUserIds : assignees, acceptorUserIds, true,
                dueAt, createdAt, System.currentTimeMillis());
    }

    public ProjectWorkDetail assign(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            throw new IllegalArgumentException("分配用户不能为空");
        }
        return new ProjectWorkDetail(id, projectId, title, description, statusCode, assignmentMode, requiredAssigneeCount,
                candidateUserIds, assignees, acceptorUserIds, true, dueAt, createdAt, System.currentTimeMillis());
    }

    public ProjectWorkDetail claim(String userId) {
        String safeUserId = requireText(userId, "认领用户不能为空");
        if (!published) {
            throw new IllegalArgumentException("任务发布后才可以认领");
        }
        if (assignmentMode != ProjectAssignmentMode.CLAIM) {
            throw new IllegalArgumentException("该工作细节不支持自主认领");
        }
        if (assigneeUserIds.contains(safeUserId)) {
            return this;
        }
        if (assigneeUserIds.size() >= requiredAssigneeCount) {
            throw new IllegalArgumentException("该工作细节认领人数已满");
        }
        if (!candidateUserIds.isEmpty() && !candidateUserIds.contains(safeUserId)) {
            throw new IllegalArgumentException("当前用户不在可认领范围内");
        }
        List<String> nextAssignees = new java.util.ArrayList<>(assigneeUserIds);
        nextAssignees.add(safeUserId);
        return new ProjectWorkDetail(id, projectId, title, description, statusCode, assignmentMode, requiredAssigneeCount,
                candidateUserIds, normalizeIds(nextAssignees), acceptorUserIds, true, dueAt, createdAt, System.currentTimeMillis());
    }

    public boolean claimableBy(String userId) {
        String safeUserId = text(userId);
        return !safeUserId.isBlank()
                && published
                && assignmentMode == ProjectAssignmentMode.CLAIM
                && !assigneeUserIds.contains(safeUserId)
                && assigneeUserIds.size() < requiredAssigneeCount
                && (candidateUserIds.isEmpty() || candidateUserIds.contains(safeUserId));
    }

    public ProjectWorkDetail withStatus(String nextStatusCode) {
        return new ProjectWorkDetail(id, projectId, title, description, requireText(nextStatusCode, "状态不能为空"),
                assignmentMode, requiredAssigneeCount, candidateUserIds, assigneeUserIds, acceptorUserIds,
                published, dueAt, createdAt, System.currentTimeMillis());
    }

    public boolean canAccept(String userId, ProjectProgressProject project) {
        String safeUserId = text(userId);
        return acceptorUserIds.contains(safeUserId) || project.canManage(safeUserId);
    }

    public boolean assignedTo(String userId) {
        return assigneeUserIds.contains(text(userId));
    }

    private static List<String> normalizeIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
