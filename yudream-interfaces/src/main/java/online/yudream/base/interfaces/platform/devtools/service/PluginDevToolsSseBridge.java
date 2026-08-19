package online.yudream.base.interfaces.platform.devtools.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.agent.event.AgentTraceEvent;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开发者工具 SSE 桥：把插件生命周期事件与 Agent 追踪事件实时推送到调试抽屉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginDevToolsSseBridge {

    private static final long EMITTER_TIMEOUT = 30 * 60_000L;

    private final ObjectMapper objectMapper;
    private final Map<String, SseEmitter> lifecycleEmitters = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> traceEmitters = new ConcurrentHashMap<>();

    public SseEmitter connectLifecycle() {
        return connect(lifecycleEmitters);
    }

    public SseEmitter connectTraces() {
        return connect(traceEmitters);
    }

    @EventListener
    public void onLifecycle(PluginLifecycleEvent event) {
        broadcast(lifecycleEmitters, "plugin-lifecycle", event);
    }

    @EventListener
    public void onTrace(AgentTraceEvent event) {
        broadcast(traceEmitters, "agent-trace", event);
    }

    private SseEmitter connect(Map<String, SseEmitter> emitters) {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        send(emitters, id, "connected", "开发者工具事件流已连接");
        return emitter;
    }

    private void broadcast(Map<String, SseEmitter> emitters, String eventName, Object payload) {
        if (emitters.isEmpty()) {
            return;
        }
        String data;
        try {
            data = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.debug("Serialize devtools SSE payload failed: event={}", eventName, e);
            return;
        }
        emitters.keySet().forEach(id -> send(emitters, id, eventName, data));
    }

    private void send(Map<String, SseEmitter> emitters, String id, String eventName, String data) {
        SseEmitter emitter = emitters.get(id);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(id);
            emitter.complete();
        }
    }
}
