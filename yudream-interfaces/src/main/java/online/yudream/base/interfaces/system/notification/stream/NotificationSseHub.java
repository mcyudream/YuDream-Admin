package online.yudream.base.interfaces.system.notification.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 站内通知 SSE 连接注册表：按用户维护 emitter，通知创建事件到达时向该
 * 用户全部在线连接推送刷新信号。连接断开/超时自动摘除。
 */
@Slf4j
@Component
public class NotificationSseHub {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 注册一个在线连接；断开/超时/异常自动摘除。 */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> set = emitters.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable remove = () -> {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(userId, set);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(error -> remove.run());
        try {
            // 立即回一条注释帧，让 fetch 流式读取尽早确认连接建立。
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException error) {
            remove.run();
        }
        return emitter;
    }

    /** 在线连接数（运维观测用）。 */
    public int connections() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }

    /** 向指定用户全部在线连接推送通知刷新信号。 */
    public void push(Long userId, String eventName, Object payload) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload == null ? "{}" : payload, MediaType.APPLICATION_JSON));
            } catch (Exception error) {
                set.remove(emitter);
                log.debug("通知 SSE 推送失败，连接已摘除：userId={}", userId);
            }
        }
    }
}
