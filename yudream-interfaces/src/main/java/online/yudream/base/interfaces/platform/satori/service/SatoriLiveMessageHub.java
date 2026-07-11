package online.yudream.base.interfaces.platform.satori.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.satori.assembler.SatoriConversationAssembler;
import online.yudream.base.domain.platform.satori.event.SatoriEventPublished;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriConversationWebAssembler;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Connection-scoped chat SSE fan-out fed by the existing Satori WebSocket event pipeline. */
@Service
@RequiredArgsConstructor
public class SatoriLiveMessageHub {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final ObjectMapper objectMapper;
    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long connectionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String id = UUID.randomUUID().toString();
        emitters.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>()).put(id, emitter);
        emitter.onCompletion(() -> remove(connectionId, id));
        emitter.onTimeout(() -> remove(connectionId, id));
        emitter.onError(ignored -> remove(connectionId, id));
        return emitter;
    }

    @EventListener
    public void publish(SatoriEventPublished published) {
        if (published == null || published.connectionId() == null || published.event() == null
                || !"message-created".equalsIgnoreCase(published.event().type()) || published.event().message() == null) return;
        Map<String, SseEmitter> subscribers = emitters.get(published.connectionId());
        if (subscribers == null) return;
        String channelId = published.event().channel() == null ? null : published.event().channel().id();
        Object payload = SatoriConversationWebAssembler.toRes(
                SatoriConversationAssembler.toMessage(published.event().message(), channelId, published.event().user(),
                        published.event().member(), published.event().timestamp()));
        subscribers.forEach((id, emitter) -> {
            if (!send(emitter, "message", payload)) remove(published.connectionId(), id);
        });
    }

    private boolean send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (IOException | IllegalStateException exception) {
            // A browser close/reload aborts the HTTP stream. Complete normally so MVC does not route it to JSON errors.
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
            }
            return false;
        }
    }

    private void remove(Long connectionId, String id) {
        Map<String, SseEmitter> subscribers = emitters.get(connectionId);
        if (subscribers == null) return;
        subscribers.remove(id);
        if (subscribers.isEmpty()) emitters.remove(connectionId, subscribers);
    }
}
