package online.yudream.base.interfaces.platform.realtime.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 实时推送（系统工具层）。
 *
 * <p>SSE 是平台基础设施而非可配置的业务能力：端点常开、没有启停开关，也不参与平台能力闸门，
 * 因此这里只保留连接注册表、并发上限与推送原语，不再有 describe/health/enable/disable/test 契约。
 */
@Slf4j
@Component
public class SseConnectionService {

    private static final long DEFAULT_TIMEOUT = 300_000L;
    /** 并发连接硬上限：防止匿名/异常客户端连接洪泛拖垮堆内存。 */
    private static final int MAX_CONNECTIONS = 500;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 建立一条 SSE 连接；超过并发上限时拒绝并抛业务异常。 */
    public SseEmitter connect() {
        if (emitters.size() >= MAX_CONNECTIONS) {
            throw new BizException("SSE 连接数已达上限，请稍后重试");
        }
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        send(id, "connected", "SSE 已连接");
        return emitter;
    }

    /** 当前活跃连接数。 */
    public int connections() {
        return emitters.size();
    }

    /** 向所有连接广播一条命名事件。 */
    public void broadcast(String event, Object data) {
        emitters.keySet().forEach(id -> send(id, event, data));
    }

    private void send(String id, String event, Object data) {
        SseEmitter emitter = emitters.get(id);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        }
        catch (IOException e) {
            emitters.remove(id);
            emitter.completeWithError(e);
        }
    }

    /** 应用关闭时结束残留连接，避免容器等待超时。 */
    @PreDestroy
    public void closeAll() {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.complete();
            }
            catch (RuntimeException e) {
                log.debug("SSE 连接关闭失败：{}", id, e);
            }
        });
        emitters.clear();
    }
}
