package online.yudream.base.domain.platform.milky.sandbox;

import java.util.List;

// 沙盒用例中的一条待回放步骤，clientMessageId 不持久化（回放时由会话重新生成）；
// type 支持 message/group_request/button，缺省按 message 兼容旧版用例 JSON
public record QqSandboxCaseStep(
        String senderId,
        String nickname,
        String content,
        boolean mentionSelf,
        List<String> mentions,
        String replyMessageId,
        String type,
        String buttonId
) {
    public QqSandboxCaseStep {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        type = type == null || type.isBlank() ? "message" : type.trim();
    }
}
