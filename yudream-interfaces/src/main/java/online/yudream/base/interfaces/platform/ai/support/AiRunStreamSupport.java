package online.yudream.base.interfaces.platform.ai.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** SSE 仅订阅持久运行；完成、超时与网络错误只释放此订阅。 */
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiRunStreamSupport {
    private final AiRunAppService runs;
    @Value("${yudream.platform.ai.client.sse-timeout:30m}") private Duration timeout;

    public SseEmitter subscribe(Long userId, String id, long after) { return subscribe(userId, id, after, false); }
    public SseEmitter subscribe(Long userId, String id, long after, boolean agui) {
        runs.get(userId, id); // 在提交 HTTP SSE 响应前校验登录、归属与能力。
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> { closed.set(true); emitter.complete(); });
        emitter.onError(ignored -> closed.set(true));
        Thread.startVirtualThread(() -> {
            long cursor = Math.max(0, after);
            long heartbeat = System.nanoTime();
            try {
                while (!closed.get()) {
                    var events = runs.events(userId, id, cursor);
                    for (var event : events) {
                        if (closed.get()) return;
                        Object data = agui ? AiRunWebAssembler.agui(event) : AiRunWebAssembler.event(event);
                        String name = agui ? ((online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes) data).getType() : event.event();
                        emitter.send(SseEmitter.event().id(String.valueOf(event.cursor())).name(name).data(data, MediaType.APPLICATION_JSON));
                        cursor = event.cursor();
                    }
                    if (events.size() < 256 && runs.finished(userId, id, cursor)) { emitter.complete(); return; }
                    if (System.nanoTime() - heartbeat > Duration.ofSeconds(15).toNanos()) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                        heartbeat = System.nanoTime();
                    }
                    if (events.size() < 256) Thread.sleep(350);
                }
            } catch (Exception ignored) {
                // 传输与订阅鉴权失败均不取消 Harness；结构化业务错误已进入持久事件。
                emitter.complete();
            } finally { closed.set(true); }
        });
        return emitter;
    }
}
