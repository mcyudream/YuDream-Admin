package online.yudream.base.interfaces.platform.satori.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.satori.event.SatoriOperationLogPublished;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriConnectionWebAssembler;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Connection-scoped SSE fan-out for the Satori live log console. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SatoriLiveLogHub {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final ObjectMapper objectMapper;
    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long connectionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String id = UUID.randomUUID().toString();
        emitters.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>()).put(id, emitter);
        emitter.onCompletion(() -> remove(connectionId, id));
        emitter.onTimeout(() -> remove(connectionId, id));
        send(emitter, "connected", Map.of("connectionId", String.valueOf(connectionId)));
        return emitter;
    }

    @EventListener
    public void publish(SatoriOperationLogPublished published) {
        if (published == null || published.log() == null || published.log().getConnectionId() == null) return;
        Map<String, SseEmitter> subscribers = emitters.get(published.log().getConnectionId());
        if (subscribers == null) return;
        Object payload = SatoriConnectionWebAssembler.toLogRes(
                online.yudream.base.application.platform.satori.assembler.SatoriConnectionAssembler.toLogDTO(published.log()));
        subscribers.forEach((id, emitter) -> {
            if (!send(emitter, "log", payload)) remove(published.log().getConnectionId(), id);
        });
    }

    private boolean send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (IOException exception) {
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
