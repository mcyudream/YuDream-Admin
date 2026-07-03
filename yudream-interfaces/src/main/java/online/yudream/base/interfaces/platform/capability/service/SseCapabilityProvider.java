package online.yudream.base.interfaces.platform.capability.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SseCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "sse";

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "SSE 实时推送",
                CapabilityType.REALTIME,
                "提供浏览器 EventSource 单向实时推送能力",
                "i-ri:broadcast-line",
                100,
                Map.of("timeout", "300000")
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("SSE 未启用");
        }
        return CapabilityHealth.enabled("SSE 运行中", Map.of("connections", emitters.size()));
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
        emitters.forEach((id, emitter) -> emitter.complete());
        emitters.clear();
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("SSE 未启用");
        }
        broadcast("capability-test", message);
        return CapabilityTestResult.success("SSE 测试消息已推送，连接数：" + emitters.size());
    }

    public SseEmitter connect() {
        if (!enabled.get()) {
            throw new BizException("SSE 能力未启用，请先在平台能力中启用");
        }
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        send(id, "connected", "SSE 已连接");
        return emitter;
    }

    private void broadcast(String event, String message) {
        emitters.keySet().forEach(id -> send(id, event, message));
    }

    private void send(String id, String event, String message) {
        SseEmitter emitter = emitters.get(id);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(message));
        } catch (IOException e) {
            emitters.remove(id);
            emitter.completeWithError(e);
        }
    }
}
