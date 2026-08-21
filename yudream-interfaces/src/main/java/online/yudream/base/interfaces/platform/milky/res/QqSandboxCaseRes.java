package online.yudream.base.interfaces.platform.milky.res;

import java.time.Instant;
import java.util.List;

public record QqSandboxCaseRes(
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        QqSandboxCaseSetupRes setup,
        List<QqSandboxCaseStepRes> steps
) {
    public record QqSandboxCaseSetupRes(
            String pluginCode,
            String policyConnectionId,
            String selfId,
            String userId,
            String nickname,
            String channelId,
            String scene,
            String randomMode,
            boolean forceUnbound,
            List<String> simulateRoles
    ) { }

    public record QqSandboxCaseStepRes(
            String senderId,
            String nickname,
            String content,
            boolean mentionSelf,
            List<String> mentions,
            String replyMessageId,
            String type,
            String buttonId
    ) { }
}
