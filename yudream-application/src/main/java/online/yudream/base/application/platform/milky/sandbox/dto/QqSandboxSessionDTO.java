package online.yudream.base.application.platform.milky.sandbox.dto;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;

import java.time.Instant;
import java.util.List;

public record QqSandboxSessionDTO(
        String id,
        String pluginCode,
        String connectionId,
        String policyConnectionId,
        String selfId,
        String userId,
        String nickname,
        String channelId,
        String scene,
        QqSandboxRandomMode randomMode,
        long timeoutMillis,
        String status,
        Instant createdAt,
        List<QqSandboxTimelineEventDTO> timeline
) { }
