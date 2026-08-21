package online.yudream.base.domain.platform.milky.sandbox;

import java.util.List;

// 沙盒用例的会话初始参数，与创建会话命令同形但不携带超时（回放沿用默认超时）
public record QqSandboxCaseSetup(
        String pluginCode,
        String policyConnectionId,
        String selfId,
        String userId,
        String nickname,
        String channelId,
        String scene,
        QqSandboxRandomMode randomMode,
        boolean forceUnbound,
        List<String> simulateRoles
) {
    public QqSandboxCaseSetup {
        if (randomMode == null) randomMode = QqSandboxRandomMode.REAL;
        simulateRoles = simulateRoles == null ? null : List.copyOf(simulateRoles);
    }
}
