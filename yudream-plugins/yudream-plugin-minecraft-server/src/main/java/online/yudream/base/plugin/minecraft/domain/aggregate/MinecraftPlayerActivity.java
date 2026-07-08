package online.yudream.base.plugin.minecraft.domain.aggregate;

public record MinecraftPlayerActivity(
        String id,
        String serverId,
        String playerId,
        String playerName,
        long totalOnlineMillis,
        long totalAfkMillis,
        Long currentOnlineSince,
        Long currentAfkSince,
        Long lastJoinedAt,
        Long lastQuitAt,
        long createdAt,
        long updatedAt
) {

    public MinecraftPlayerActivity {
        serverId = requireText(serverId, "服务器 ID 不能为空");
        playerId = requireText(playerId, "玩家 ID 不能为空");
        id = id == null || id.isBlank() ? id(serverId, playerId) : id.trim();
        playerName = normalizeName(playerName, playerId);
        totalOnlineMillis = Math.max(totalOnlineMillis, 0);
        totalAfkMillis = Math.max(totalAfkMillis, 0);
        if (currentOnlineSince == null) {
            currentAfkSince = null;
        }
        createdAt = createdAt <= 0 ? System.currentTimeMillis() : createdAt;
        updatedAt = updatedAt <= 0 ? createdAt : updatedAt;
    }

    public static MinecraftPlayerActivity empty(String serverId, String playerId, String playerName, long eventAt) {
        return new MinecraftPlayerActivity(null, serverId, playerId, playerName, 0, 0,
                null, null, null, null, eventAt, eventAt);
    }

    public MinecraftPlayerActivity join(String nextPlayerName, long eventAt) {
        if (currentOnlineSince != null) {
            return rename(nextPlayerName, eventAt);
        }
        return new MinecraftPlayerActivity(id, serverId, playerId, nameOrCurrent(nextPlayerName), totalOnlineMillis, totalAfkMillis,
                eventAt, null, eventAt, lastQuitAt, createdAt, eventAt);
    }

    public MinecraftPlayerActivity quit(String nextPlayerName, long eventAt) {
        long onlineMillis = totalOnlineMillis + duration(currentOnlineSince, eventAt);
        long afkMillis = totalAfkMillis + duration(currentAfkSince, eventAt);
        return new MinecraftPlayerActivity(id, serverId, playerId, nameOrCurrent(nextPlayerName), onlineMillis, afkMillis,
                null, null, lastJoinedAt, eventAt, createdAt, eventAt);
    }

    public MinecraftPlayerActivity startAfk(String nextPlayerName, long eventAt) {
        MinecraftPlayerActivity online = currentOnlineSince == null ? join(nextPlayerName, eventAt) : rename(nextPlayerName, eventAt);
        if (online.currentAfkSince != null) {
            return online;
        }
        return new MinecraftPlayerActivity(online.id, online.serverId, online.playerId, online.playerName,
                online.totalOnlineMillis, online.totalAfkMillis, online.currentOnlineSince, eventAt,
                online.lastJoinedAt, online.lastQuitAt, online.createdAt, eventAt);
    }

    public MinecraftPlayerActivity endAfk(String nextPlayerName, long eventAt) {
        if (currentAfkSince == null) {
            return rename(nextPlayerName, eventAt);
        }
        return new MinecraftPlayerActivity(id, serverId, playerId, nameOrCurrent(nextPlayerName), totalOnlineMillis,
                totalAfkMillis + duration(currentAfkSince, eventAt), currentOnlineSince, null,
                lastJoinedAt, lastQuitAt, createdAt, eventAt);
    }

    public boolean online() {
        return currentOnlineSince != null;
    }

    public boolean afk() {
        return currentAfkSince != null;
    }

    public long totalOnlineMillisAt(long now) {
        return totalOnlineMillis + duration(currentOnlineSince, now);
    }

    public long totalAfkMillisAt(long now) {
        return totalAfkMillis + duration(currentAfkSince, now);
    }

    public static String id(String serverId, String playerId) {
        return requireText(serverId, "服务器 ID 不能为空") + ":" + requireText(playerId, "玩家 ID 不能为空");
    }

    private MinecraftPlayerActivity rename(String nextPlayerName, long eventAt) {
        return new MinecraftPlayerActivity(id, serverId, playerId, nameOrCurrent(nextPlayerName), totalOnlineMillis, totalAfkMillis,
                currentOnlineSince, currentAfkSince, lastJoinedAt, lastQuitAt, createdAt, eventAt);
    }

    private String nameOrCurrent(String nextPlayerName) {
        return nextPlayerName == null || nextPlayerName.isBlank() ? playerName : nextPlayerName;
    }

    private static long duration(Long startAt, long endAt) {
        if (startAt == null || endAt <= startAt) {
            return 0;
        }
        return endAt - startAt;
    }

    private static String normalizeName(String value, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        return text.length() > 64 ? text.substring(0, 64) : text;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
