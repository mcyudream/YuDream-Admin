package online.yudream.base.plugin.projectprogress.domain.enumerate;

public enum ProjectAssignmentMode {
    CLAIM,
    RANDOM;

    public static ProjectAssignmentMode of(String value) {
        if (value == null || value.isBlank()) {
            return CLAIM;
        }
        return ProjectAssignmentMode.valueOf(value.trim().toUpperCase());
    }
}
