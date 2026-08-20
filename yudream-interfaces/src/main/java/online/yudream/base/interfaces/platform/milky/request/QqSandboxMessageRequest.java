package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record QqSandboxMessageRequest(
        String senderId,
        String nickname,
        @NotBlank String content,
        boolean mentionSelf,
        List<String> mentions,
        String replyMessageId,
        String clientMessageId
) { }
