package online.yudream.base.interfaces.platform.wiki.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiPublicationProgressAppService;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiProgressWebAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class WikiPublicationProgressStreamSupport {

    private static final long TIMEOUT_MILLIS = 600_000L;

    private final WikiPublicationProgressAppService service;

    public SseEmitter subscribe(Long nodeId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        AutoCloseable subscription = service.subscribe(nodeId,
                item -> send(emitter, WikiProgressWebAssembler.toRes(item)));
        emitter.onCompletion(() -> close(subscription));
        emitter.onTimeout(() -> close(subscription));
        emitter.onError(error -> close(subscription));
        try {
            emitter.send(SseEmitter.event().name("wiki.progress").data(java.util.Map.of(
                    "event", "wiki.progress", "action", "subscribed", "module", "wiki")));
        }
        catch (IOException error) {
            close(subscription);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("wiki.progress").data(data));
            }
        }
        catch (IOException error) {
            emitter.completeWithError(error);
        }
    }

    private void close(AutoCloseable subscription) {
        try {
            subscription.close();
        }
        catch (Exception ignored) {
        }
    }
}
