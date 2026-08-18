package online.yudream.base.interfaces.platform.wiki.support;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiAguiWebAssembler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 公开 Wiki 问答的 SSE 执行编排。
 *
 * <p>把「限流租约 + SseEmitter 生命周期 + 有界线程池提交 + 心跳 + 取消协调」从 Controller 收敛到独立 support，
 * 便于单测可靠地验证超时/错误取消、正常完成不误取消、提交被拒绝时归还名额等边界。</p>
 *
 * <p>关键取消语义：{@link SseEmitter} 的超时、客户端异常、正常完成都会回调 onCompletion。为避免正常
 * {@code emitter.complete()} 触发的 onCompletion 反向取消「刚完成任务」，用 {@code taskFinished} 标记任务自然结束，
 * 只有任务尚未结束时回调才执行取消；{@code transportTerminated} 保证取消只发生一次。</p>
 */
@Slf4j
@Component
public class PublicWikiChatExecution {

    /** 实际执行公开 Wiki 流式问答的钩子，Controller 只负责接线到应用服务，便于测试注入阻塞型假任务。 */
    @FunctionalInterface
    public interface ChatWork {
        WikiChatResultDTO run(Consumer<String> onDelta,
                              Consumer<String> onReasoningDelta,
                              Consumer<AiAgentToolResult> onTool,
                              Consumer<WikiChatActivityDTO> onActivity);
    }

    /** 启动结果：emitter 交由 Spring 返回给客户端，future 供测试或调用方观察/取消。 */
    public record Started(SseEmitter emitter, Future<?> future) {
    }

    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000L;

    private final ThreadPoolTaskExecutor executor;
    private final PublicWikiChatRateLimiter rateLimiter;
    private final Duration timeout;

    public PublicWikiChatExecution(
            @Qualifier("publicWikiChatExecutor") ThreadPoolTaskExecutor executor,
            PublicWikiChatRateLimiter rateLimiter,
            @Value("${yudream.platform.wiki.chat.public-sse-timeout:3m}") Duration timeout) {
        this.executor = executor;
        this.rateLimiter = rateLimiter;
        this.timeout = timeout;
    }

    /**
     * 占用一个公开问答租约并提交流式任务。调用方在拿到 emitter 前即可返回，任务在专用线程池中执行。
     *
     * @param remoteAddr 容器解析出的可信客户端地址，用于限流维度
     * @param spaceSlug 公开知识库 slug，用于限流维度
     * @param work 阻塞的流式问答逻辑；被取消时线程会收到中断
     */
    public Started start(String remoteAddr, String spaceSlug, ChatWork work) {
        PublicWikiChatRateLimiter.Permit permit = rateLimiter.acquire(remoteAddr, spaceSlug);
        SseEmitter emitter;
        try {
            emitter = new SseEmitter(timeout.toMillis());
        } catch (RuntimeException | Error e) {
            permit.close();
            throw e;
        }

        String traceId = UUID.randomUUID().toString();
        AtomicBoolean taskFinished = new AtomicBoolean(false);
        AtomicBoolean transportTerminated = new AtomicBoolean(false);
        AtomicBoolean permitReleased = new AtomicBoolean(false);
        AtomicBoolean taskStarted = new AtomicBoolean(false);
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Thread> heartbeatThread = new AtomicReference<>();
        AtomicReference<Future<?>> taskFuture = new AtomicReference<>();

        // 租约释放与任务取消共享幂等句柄，覆盖任务开始前被取消的排队任务。
        Runnable releasePermit = () -> {
            if (permitReleased.compareAndSet(false, true)) {
                permit.close();
            }
        };
        Runnable cancelTransport = () -> {
            if (!transportTerminated.compareAndSet(false, true)) {
                return;
            }
            running.set(false);
            Thread heartbeat = heartbeatThread.get();
            if (heartbeat != null) {
                heartbeat.interrupt();
            }
            Future<?> future = taskFuture.get();
            if (future != null) {
                future.cancel(true);
            }
            if (!taskStarted.get()) {
                releasePermit.run();
            }
        };
        emitter.onTimeout(cancelTransport);
        emitter.onError(error -> cancelTransport.run());
        emitter.onCompletion(() -> {
            if (!taskFinished.get()) {
                cancelTransport.run();
            }
        });

        Runnable task = () -> {
            taskStarted.set(true);
            AtomicInteger toolSequence = new AtomicInteger();
            try {
                if (transportTerminated.get()) {
                    return;
                }
                startHeartbeat(emitter, running, heartbeatThread, cancelTransport);
                send(emitter, WikiAguiWebAssembler.runStarted(traceId), cancelTransport);
                send(emitter, WikiAguiWebAssembler.activitySnapshot(traceId, "accepted", "已收到问题，正在连接模型。"), cancelTransport);
                WikiChatResultDTO result = work.run(
                        delta -> send(emitter, WikiAguiWebAssembler.textChunk(traceId, delta), cancelTransport),
                        reasoning -> {
                            if (thinkingStarted.compareAndSet(false, true)) {
                                send(emitter, WikiAguiWebAssembler.thinkingStart(traceId), cancelTransport);
                            }
                            send(emitter, WikiAguiWebAssembler.thinkingContent(traceId, reasoning), cancelTransport);
                        },
                        tool -> {
                            String toolCallId = traceId + "-tool-" + toolSequence.incrementAndGet();
                            send(emitter, WikiAguiWebAssembler.toolStart(traceId, toolCallId, tool), cancelTransport);
                            send(emitter, WikiAguiWebAssembler.toolResult(traceId, toolCallId, tool), cancelTransport);
                        },
                        activity -> send(emitter, WikiAguiWebAssembler.activitySnapshot(traceId, activity), cancelTransport));
                if (transportTerminated.get()) {
                    // 传输已因超时/客户端断开而取消，任务随后被中断；不再发送正常完成事件。
                    return;
                }
                taskFinished.set(true);
                if (thinkingStarted.compareAndSet(true, false)) {
                    send(emitter, WikiAguiWebAssembler.thinkingEnd(traceId), cancelTransport);
                }
                send(emitter, WikiAguiWebAssembler.runFinished(traceId, result), cancelTransport);
                emitter.complete();
            } catch (Exception e) {
                if (transportTerminated.get()) {
                    // 传输已因超时/客户端断开而终止，任务随后被取消，无需再向已关闭的 emitter 写错误事件。
                    log.debug("Public wiki chat AG-UI stream cancelled, slug={}", spaceSlug);
                } else {
                    log.debug("Public wiki chat AG-UI stream failed, slug={}", spaceSlug, e);
                    taskFinished.set(true);
                    if (thinkingStarted.compareAndSet(true, false)) {
                        send(emitter, WikiAguiWebAssembler.thinkingEnd(traceId), cancelTransport);
                    }
                    send(emitter, WikiAguiWebAssembler.runError(traceId, e.getMessage()), cancelTransport);
                    emitter.complete();
                }
            } finally {
                running.set(false);
                Thread heartbeat = heartbeatThread.get();
                if (heartbeat != null) {
                    heartbeat.interrupt();
                }
                releasePermit.run();
            }
        };

        Future<?> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            // 有界线程池已满：立即归还并发名额，并让 emitter 明确结束，避免悬挂。
            releasePermit.run();
            emitter.completeWithError(e);
            throw e;
        }
        taskFuture.set(future);
        if (transportTerminated.get()) {
            // 容器回调可能早于 future 引用写入；补偿取消，避免任务在连接断开后继续运行。
            future.cancel(true);
        }
        return new Started(emitter, future);
    }

    private void send(SseEmitter emitter, AguiStreamEventRes data, Runnable cancelTransport) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(data.getType()).data(data, MediaType.APPLICATION_JSON));
            }
        } catch (IOException e) {
            log.debug("Public wiki chat AG-UI send failed, type={}", data.getType(), e);
            cancelTransport.run();
            emitter.completeWithError(e);
            throw new SseTransportException(e);
        }
    }

    /**
     * 启动独立心跳线程维持 SSE 连接；线程持有 interrupt 引用，取消时直接打断 sleep，避免残留 15s 定时线程。
     */
    private void startHeartbeat(SseEmitter emitter,
                                AtomicBoolean running,
                                AtomicReference<Thread> heartbeatThread,
                                Runnable cancelTransport) {
        Thread thread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!running.get()) {
                    return;
                }
                try {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    }
                } catch (IOException e) {
                    log.debug("Public wiki chat AG-UI heartbeat failed", e);
                    cancelTransport.run();
                    emitter.completeWithError(e);
                    return;
                }
            }
        }, "public-wiki-chat-heartbeat-" + UUID.randomUUID());
        thread.setDaemon(true);
        heartbeatThread.set(thread);
        thread.start();
    }

    private static final class SseTransportException extends RuntimeException {
        private SseTransportException(IOException cause) {
            super(cause);
        }
    }
}
