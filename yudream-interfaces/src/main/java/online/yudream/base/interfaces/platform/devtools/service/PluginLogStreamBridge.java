package online.yudream.base.interfaces.platform.devtools.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.devtools.service.PluginDevToolsAppService;
import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.interfaces.platform.devtools.assembler.PluginDevToolsWebAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 插件日志 SSE 桥：为单个连接建立按插件过滤的日志订阅，连接断开时取消订阅。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginLogStreamBridge {

    private static final long EMITTER_TIMEOUT = 30 * 60_000L;

    private final ObjectMapper objectMapper;
    private final PluginDevToolsAppService devToolsAppService;

    public SseEmitter connect(String pluginCode, String level) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        AtomicReference<AutoCloseable> subscriptionRef = new AtomicReference<>();
        Runnable cleanup = () -> {
            AutoCloseable subscription = subscriptionRef.getAndSet(null);
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception e) {
                    log.debug("关闭插件日志订阅失败：plugin={}", pluginCode, e);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(throwable -> cleanup.run());

        AutoCloseable subscription = devToolsAppService.subscribePluginLogs(pluginCode, level,
                entry -> send(emitter, cleanup, entry));
        // 订阅回调可能在 set 之前触发（缓冲补发），此时 send 失败的 cleanup 拿不到句柄；
        // 该竞态窗口极短，订阅句柄会随后续回调的失败路径或连接超时回收，可接受。
        subscriptionRef.set(subscription);
        sendRaw(emitter, cleanup, "connected", "\"插件日志流已连接\"");
        return emitter;
    }

    private void send(SseEmitter emitter, Runnable cleanup, SystemLogEntry entry) {
        String data;
        try {
            data = objectMapper.writeValueAsString(PluginDevToolsWebAssembler.toLogRes(entry));
        } catch (Exception e) {
            log.debug("序列化插件日志事件失败", e);
            return;
        }
        sendRaw(emitter, cleanup, "plugin-log", data);
    }

    private void sendRaw(SseEmitter emitter, Runnable cleanup, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            cleanup.run();
            emitter.complete();
        }
    }
}
