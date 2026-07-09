package online.yudream.base.plugin.projectprogress.domain.aggregate;

import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectCheckInType;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftPolicy;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectStatusOption;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProjectProgressProject(
        String id,
        String name,
        String description,
        List<String> managerUserIds,
        List<String> memberUserIds,
        List<ProjectStatusOption> statuses,
        String defaultStatusCode,
        String doneStatusCode,
        String reworkStatusCode,
        int minCheckInIntervalMinutes,
        List<ProjectCheckInType> allowedCheckInTypes,
        ProjectMinecraftPolicy minecraftPolicy,
        boolean enabled,
        long createdAt,
        long updatedAt
) {

    public ProjectProgressProject {
        id = requireText(id, "项目 ID 不能为空");
        name = requireText(name, "项目名称不能为空");
        description = text(description);
        managerUserIds = normalizeIds(managerUserIds);
        memberUserIds = normalizeIds(memberUserIds);
        memberUserIds = mergeIds(memberUserIds, managerUserIds);
        statuses = normalizeStatuses(statuses);
        defaultStatusCode = normalizeStatusCode(defaultStatusCode);
        doneStatusCode = normalizeStatusCode(doneStatusCode);
        reworkStatusCode = normalizeStatusCode(reworkStatusCode);
        ensureStatusExists(statuses, defaultStatusCode, "默认状态不存在");
        ensureStatusExists(statuses, doneStatusCode, "完成状态不存在");
        if (reworkStatusCode != null) {
            ensureStatusExists(statuses, reworkStatusCode, "返工状态不存在");
        }
        minCheckInIntervalMinutes = Math.max(minCheckInIntervalMinutes, 0);
        allowedCheckInTypes = allowedCheckInTypes == null || allowedCheckInTypes.isEmpty()
                ? List.of(ProjectCheckInType.IMAGE, ProjectCheckInType.FILE, ProjectCheckInType.LOCATION)
                : List.copyOf(new LinkedHashSet<>(allowedCheckInTypes));
        minecraftPolicy = minecraftPolicy == null ? ProjectMinecraftPolicy.disabled() : minecraftPolicy;
    }

    public static ProjectProgressProject create(String name, String description, List<String> managerUserIds,
                                                List<String> memberUserIds, List<ProjectStatusOption> statuses,
                                                String defaultStatusCode, String doneStatusCode, String reworkStatusCode,
                                                int minCheckInIntervalMinutes, List<ProjectCheckInType> allowedCheckInTypes,
                                                ProjectMinecraftPolicy minecraftPolicy, boolean enabled) {
        long now = System.currentTimeMillis();
        List<ProjectStatusOption> safeStatuses = statuses == null || statuses.isEmpty() ? defaultStatuses() : statuses;
        return new ProjectProgressProject(UUID.randomUUID().toString(), name, description, managerUserIds, memberUserIds,
                safeStatuses, defaultStatusCode == null ? "TODO" : defaultStatusCode, doneStatusCode == null ? "DONE" : doneStatusCode,
                reworkStatusCode, minCheckInIntervalMinutes, allowedCheckInTypes, minecraftPolicy, enabled, now, now);
    }

    public ProjectProgressProject update(String name, String description, List<String> managerUserIds,
                                         List<String> memberUserIds, List<ProjectStatusOption> statuses,
                                         String defaultStatusCode, String doneStatusCode, String reworkStatusCode,
                                         int minCheckInIntervalMinutes, List<ProjectCheckInType> allowedCheckInTypes,
                                         ProjectMinecraftPolicy minecraftPolicy, boolean enabled) {
        return new ProjectProgressProject(id, name, description, managerUserIds, memberUserIds, statuses,
                defaultStatusCode, doneStatusCode, reworkStatusCode, minCheckInIntervalMinutes, allowedCheckInTypes,
                minecraftPolicy, enabled, createdAt, System.currentTimeMillis());
    }

    public boolean allows(ProjectCheckInType type) {
        return allowedCheckInTypes.contains(type);
    }

    public boolean containsMember(String userId) {
        return memberUserIds.contains(userId) || managerUserIds.contains(userId);
    }

    public boolean canManage(String userId) {
        return managerUserIds.contains(userId);
    }

    public ProjectProgressProject withMembers(List<String> userIds) {
        List<String> nextMembers = mergeIds(memberUserIds, userIds);
        if (nextMembers.equals(memberUserIds)) {
            return this;
        }
        return new ProjectProgressProject(id, name, description, managerUserIds, nextMembers, statuses,
                defaultStatusCode, doneStatusCode, reworkStatusCode, minCheckInIntervalMinutes, allowedCheckInTypes,
                minecraftPolicy, enabled, createdAt, System.currentTimeMillis());
    }

    public static List<ProjectStatusOption> defaultStatuses() {
        return List.of(
                new ProjectStatusOption("TODO", "未完成", false, 10),
                new ProjectStatusOption("REVIEWING", "复审中", false, 20),
                new ProjectStatusOption("REPAIRING", "修缮中", false, 30),
                new ProjectStatusOption("DONE", "完成", true, 40)
        );
    }

    private static List<ProjectStatusOption> normalizeStatuses(List<ProjectStatusOption> values) {
        List<ProjectStatusOption> result = (values == null || values.isEmpty() ? defaultStatuses() : values).stream()
                .sorted(Comparator.comparingInt(ProjectStatusOption::sort))
                .toList();
        if (result.stream().map(ProjectStatusOption::code).distinct().count() != result.size()) {
            throw new IllegalArgumentException("项目状态编码不能重复");
        }
        return result;
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

    private static List<String> mergeIds(List<String> values, List<String> additions) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(result::add);
        }
        if (additions != null) {
            additions.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static void ensureStatusExists(List<ProjectStatusOption> statuses, String code, String message) {
        if (code == null || statuses.stream().noneMatch(status -> status.code().equals(code))) {
            throw new IllegalArgumentException(message + "：" + code);
        }
    }

    private static String normalizeStatusCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
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
