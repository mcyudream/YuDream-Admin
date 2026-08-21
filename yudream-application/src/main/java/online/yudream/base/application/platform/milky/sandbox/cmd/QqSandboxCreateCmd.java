package online.yudream.base.application.platform.milky.sandbox.cmd;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;

import java.util.List;

public record QqSandboxCreateCmd(
        String pluginCode,
        String policyConnectionId,
        String selfId,
        String userId,
        String nickname,
        String channelId,
        String scene,
        QqSandboxRandomMode randomMode,
        // 身份模拟：forceUnbound 强制未绑定；simulateRoles 为 null 走真实角色、空列表表示无角色
        Boolean forceUnbound,
        List<String> simulateRoles,
        Long timeoutMillis
) { }
