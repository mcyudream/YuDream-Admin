package online.yudream.base.application.platform.milky.sandbox.cmd;

import java.util.List;

public record QqSandboxMessageCmd(
        String senderId,
        String nickname,
        String content,
        boolean mentionSelf,
        List<String> mentions,
        String replyMessageId,
        String clientMessageId,
        // 事件类型：message（默认）/ group_request（入群请求）/ button（按钮回调）
        String type,
        String buttonId
) {
    public QqSandboxMessageCmd {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        type = type == null || type.isBlank() ? "message" : type.trim();
    }
}
