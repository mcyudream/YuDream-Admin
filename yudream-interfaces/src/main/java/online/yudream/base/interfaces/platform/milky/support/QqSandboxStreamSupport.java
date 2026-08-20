package online.yudream.base.interfaces.platform.milky.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.sandbox.service.QqSandboxAppService;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxTimelineEventDTO;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxTimelineEvent;
import online.yudream.base.interfaces.platform.milky.assembler.QqSandboxWebAssembler;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxEventRes;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QqSandboxStreamSupport {
    private final QqSandboxAppService appService;

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        Object lock = new Object();
        List<QqSandboxTimelineEvent> pending = new ArrayList<>();
        long[] lastSequence = {0L};
        boolean[] replayed = {false};
        boolean[] closed = {false};
        AutoCloseable[] subscriptionRef = new AutoCloseable[1];
        Runnable shutdown = () -> {
            synchronized (lock) {
                if (closed[0]) return;
                closed[0] = true;
            }
            if (subscriptionRef[0] != null) close(subscriptionRef[0]);
        };
        try {
            AutoCloseable subscription = appService.subscribe(sessionId, event -> {
                boolean failed = false;
                synchronized (lock) {
                    if (closed[0]) return;
                    if (!replayed[0]) {
                        pending.add(event);
                        return;
                    }
                    failed = !sendOnce(emitter, QqSandboxWebAssembler.toEventRes(event, sessionId),
                            event.sequence(), lastSequence);
                }
                if (failed) failQuietly(emitter, shutdown);
            });
            subscriptionRef[0] = subscription;
            var snapshot = appService.detail(sessionId);
            boolean failed = false;
            synchronized (lock) {
                if (!closed[0]) {
                    if (send(emitter, new QqSandboxEventRes("sandbox.connected", "connected", "qq-sandbox", sessionId,
                            Instant.now(), Map.of("sessionId", snapshot.id(), "status", snapshot.status())))) {
                        List<QqSandboxTimelineEventDTO> history = snapshot.timeline().stream()
                                .sorted(Comparator.comparingLong(QqSandboxTimelineEventDTO::sequence)).toList();
                        for (QqSandboxTimelineEventDTO event : history) {
                            if (!sendOnce(emitter, QqSandboxWebAssembler.toEventRes(event, sessionId),
                                    event.sequence(), lastSequence)) {
                                failed = true;
                                break;
                            }
                        }
                        if (!failed) {
                            List<QqSandboxTimelineEvent> buffered = pending.stream()
                                    .sorted(Comparator.comparingLong(QqSandboxTimelineEvent::sequence)).toList();
                            for (QqSandboxTimelineEvent event : buffered) {
                                if (!sendOnce(emitter, QqSandboxWebAssembler.toEventRes(event, sessionId),
                                        event.sequence(), lastSequence)) {
                                    failed = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        failed = true;
                    }
                    pending.clear();
                    replayed[0] = true;
                }
            }
            if (failed) failQuietly(emitter, shutdown);
            emitter.onCompletion(shutdown);
            emitter.onTimeout(shutdown);
            emitter.onError(ignored -> shutdown.run());
        } catch (RuntimeException error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private boolean sendOnce(SseEmitter emitter, QqSandboxEventRes event, long sequence, long[] lastSequence) {
        if (sequence <= lastSequence[0]) return true;
        if (!send(emitter, event)) return false;
        lastSequence[0] = sequence;
        return true;
    }

    private boolean send(SseEmitter emitter, QqSandboxEventRes event) {
        try {
            emitter.send(SseEmitter.event().name(event.event()).data(event, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | RuntimeException error) {
            // 客户端断开后 Tomcat 会抛 IllegalStateException 而非 IOException，统一视为连接已失效
            return false;
        }
    }

    private void failQuietly(SseEmitter emitter, Runnable shutdown) {
        shutdown.run();
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
        }
    }

    private void close(AutoCloseable subscription) {
        try {
            subscription.close();
        } catch (Exception ignored) {
        }
    }
}
