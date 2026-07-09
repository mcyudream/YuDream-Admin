package online.yudream.base.plugin.projectprogress.domain.valobj;

public record ProjectMinecraftPolicy(
        boolean enabled,
        String serverId,
        int requiredOnlineMinutes,
        boolean includeAfk,
        boolean autoCheckInEnabled
) {

    public static ProjectMinecraftPolicy disabled() {
        return new ProjectMinecraftPolicy(false, null, 0, false, false);
    }

    public ProjectMinecraftPolicy {
        serverId = serverId == null || serverId.isBlank() ? null : serverId.trim();
        requiredOnlineMinutes = Math.max(requiredOnlineMinutes, 0);
        if (enabled && (serverId == null || serverId.isBlank())) {
            throw new IllegalArgumentException("启用 Minecraft 打卡时必须选择服务器");
        }
        if (enabled && requiredOnlineMinutes <= 0) {
            throw new IllegalArgumentException("Minecraft 在线时长阈值必须大于 0 分钟");
        }
    }
}
