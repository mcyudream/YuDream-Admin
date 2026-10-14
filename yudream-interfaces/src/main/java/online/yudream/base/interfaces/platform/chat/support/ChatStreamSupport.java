package online.yudream.base.interfaces.platform.chat.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.service.ChatRunAppService;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 兼容原聊天流地址，传输订阅与后台 Harness 生命周期独立。 */
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ChatStreamSupport {
    private final ChatRunAppService chatRuns;
    private final AiRunStreamSupport streams;

    public SseEmitter stream(Long userId, ChatSendCmd cmd) {
        var run = chatRuns.start(userId, cmd);
        return streams.subscribe(userId, run.id(), 0, true);
    }

    public SseEmitter streamOnce(Long userId, ChatSendCmd cmd) {
        var run = chatRuns.startOnce(userId, cmd);
        return streams.subscribe(userId, run.id(), 0, true);
    }
}
