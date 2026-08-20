package online.yudream.base.interfaces.platform.milky.res;

import java.time.Instant;
import java.util.Map;

public record QqSandboxSessionRes(
        String sessionId,
        String status,
        String conversationType,
        String pluginCode,
        String policyConnectionId,
        String botId,
        String userId,
        String groupId,
        String nickname,
        String randomMode,
        Instant createdAt,
        Instant expiresAt,
        Map<String, Object> metadata
) { }
