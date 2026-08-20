package online.yudream.base.application.platform.milky.sandbox.cmd;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;

public record QqSandboxCreateCmd(
        String pluginCode,
        String policyConnectionId,
        String selfId,
        String userId,
        String nickname,
        String channelId,
        String scene,
        QqSandboxRandomMode randomMode,
        Long timeoutMillis
) { }
