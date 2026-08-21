package online.yudream.base.interfaces.platform.milky.request;

import java.util.List;

// content 是否必填取决于 type（message 必填、button 用 buttonId、group_request 可空），由应用层按类型校验
public record QqSandboxMessageRequest(
        String senderId,
        String nickname,
        String content,
        boolean mentionSelf,
        List<String> mentions,
        String replyMessageId,
        String clientMessageId,
        String type,
        String buttonId
) { }
