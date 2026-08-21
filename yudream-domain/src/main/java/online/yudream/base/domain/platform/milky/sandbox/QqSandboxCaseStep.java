package online.yudream.base.domain.platform.milky.sandbox;

import java.util.List;

// 沙盒用例中的一条待回放消息，clientMessageId 不持久化（回放时由会话重新生成）
public record QqSandboxCaseStep(
        String senderId,
        String nickname,
        String content,
        boolean mentionSelf,
        List<String> mentions,
        String replyMessageId
) {
    public QqSandboxCaseStep {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }
}
