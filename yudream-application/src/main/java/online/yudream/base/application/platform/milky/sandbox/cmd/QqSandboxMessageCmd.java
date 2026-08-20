package online.yudream.base.application.platform.milky.sandbox.cmd;

import java.util.List;

public record QqSandboxMessageCmd(
        String senderId,
        String nickname,
        String content,
        boolean mentionSelf,
        List<String> mentions,
        String replyMessageId,
        String clientMessageId
) {
    public QqSandboxMessageCmd {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }
}
