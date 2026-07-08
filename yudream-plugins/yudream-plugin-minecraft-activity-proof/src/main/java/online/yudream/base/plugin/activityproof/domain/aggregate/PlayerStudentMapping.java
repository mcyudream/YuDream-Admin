package online.yudream.base.plugin.activityproof.domain.aggregate;

public record PlayerStudentMapping(
        String id,
        String serverId,
        String playerId,
        String playerName,
        String studentNo,
        long createdAt,
        long updatedAt
) {
    public PlayerStudentMapping {
        serverId = require(serverId, "服务器不能为空");
        playerId = require(playerId, "玩家 ID 不能为空");
        studentNo = require(studentNo, "学号不能为空");
        id = id == null || id.isBlank() ? id(serverId, playerId) : id.trim();
        playerName = playerName == null ? "" : playerName.trim();
        createdAt = createdAt <= 0 ? System.currentTimeMillis() : createdAt;
        updatedAt = updatedAt <= 0 ? createdAt : updatedAt;
    }

    public static PlayerStudentMapping create(String serverId, String playerId, String playerName, String studentNo) {
        long now = System.currentTimeMillis();
        return new PlayerStudentMapping(null, serverId, playerId, playerName, studentNo, now, now);
    }

    public PlayerStudentMapping update(String playerName, String studentNo) {
        return new PlayerStudentMapping(id, serverId, playerId, playerName, studentNo, createdAt, System.currentTimeMillis());
    }

    public static String id(String serverId, String playerId) {
        return require(serverId, "服务器不能为空") + ":" + require(playerId, "玩家 ID 不能为空");
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
