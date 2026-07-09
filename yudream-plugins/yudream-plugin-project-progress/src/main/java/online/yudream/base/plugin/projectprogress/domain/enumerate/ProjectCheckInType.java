package online.yudream.base.plugin.projectprogress.domain.enumerate;

public enum ProjectCheckInType {
    IMAGE,
    FILE,
    LOCATION,
    MINECRAFT_ONLINE;

    public static ProjectCheckInType of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("打卡类型不能为空");
        }
        return ProjectCheckInType.valueOf(value.trim().toUpperCase());
    }
}
