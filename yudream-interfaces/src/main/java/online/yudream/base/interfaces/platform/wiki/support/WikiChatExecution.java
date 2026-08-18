package online.yudream.base.interfaces.platform.wiki.support;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiAguiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 管理端 Wiki 问答执行器，统一负责 SSE 生命周期、后台任务取消和单次终态。
 */
@Slf4j
@Component
public class WikiChatExecution {

    @FunctionalInterface
    public interface ChatWork {
        WikiChatResultDTO run(Consumer<String> onDelta,
                              Consumer<String> onReasoningDelta,
                              Consumer<AiAgentToolResult> onTool,
                              Consumer<WikiChatActivityDTO> onActivity);
    }

    public record Started(SseEmitter emitter, Future<?> future) {
    }

    public Started startLegacy(Duration timeout, Long spaceId, ChatWork work) {
        return start(new SseEmitter(timeout.toMillis()), spaceId, work, false);
    }

    public Started startAgui(Duration timeout, Long spaceId, ChatWork work) {
        return start(new SseEmitter(timeout.toMillis()), spaceId, work, true);
    }

    Started start(SseEmitter emitter, Long spaceId, ChatWork work, boolean agui) {
        String traceId = UUID.randomUUID().toString();
        AtomicBoolean taskFinished = new AtomicBoolean(false);
        AtomicBoolean transportTerminated = new AtomicBoolean(false);
        AtomicBoolean terminalSent = new AtomicBoolean(false);
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Thread> taskThread = new AtomicReference<>();
        AtomicReference<Thread> heartbeatThread = new AtomicReference<>();
        AtomicReference<Future<?>> taskFuture = new AtomicReference<>();

        Runnable terminate = () -> {
            if (!transportTerminated.compareAndSet(false, true)) {
                return;
            }
            running.set(false);
            interrupt(heartbeatThread.get());
            Future<?> future = taskFuture.get();
            if (future != null) {
                future.cancel(true);
            }
            interrupt(taskThread.get());
        };
        emitter.onTimeout(terminate);
        emitter.onError(error -> terminate.run());
        emitter.onCompletion(() -> {
            if (!taskFinished.get()) {
                terminate.run();
            }
        });

        FutureTask<Void> task = new FutureTask<>(() -> {
            taskThread.set(Thread.currentThread());
            AtomicInteger toolSequence = new AtomicInteger();
            try {
                startHeartbeat(emitter, running, heartbeatThread, terminate);
                if (agui) {
                    sendAgui(emitter, WikiAguiWebAssembler.runStarted(traceId), terminate);
                    sendAgui(emitter, WikiAguiWebAssembler.activitySnapshot(
                            traceId, "accepted", "已收到问题，正在连接模型。"), terminate);
                }
                WikiChatResultDTO result = work.run(
                        delta -> {
                            if (agui) {
                                sendAgui(emitter, WikiAguiWebAssembler.textChunk(traceId, delta), terminate);
                            } else {
                                sendLegacy(emitter, "delta", Map.of("type", "delta", "text", delta), terminate);
                            }
                        },
                        reasoning -> {
                            if (agui) {
                                if (thinkingStarted.compareAndSet(false, true)) {
                                    sendAgui(emitter, WikiAguiWebAssembler.thinkingStart(traceId), terminate);
                                }
                                sendAgui(emitter, WikiAguiWebAssembler.thinkingContent(traceId, reasoning), terminate);
                            } else {
                                sendLegacy(emitter, "reasoning", Map.of(
                                        "type", "reasoning", "text", reasoning), terminate);
                            }
                        },
                        tool -> {
                            if (agui) {
                                String toolCallId = traceId + "-tool-" + toolSequence.incrementAndGet();
                                sendAgui(emitter, WikiAguiWebAssembler.toolStart(traceId, toolCallId, tool), terminate);
                                sendAgui(emitter, WikiAguiWebAssembler.toolResult(traceId, toolCallId, tool), terminate);
                            } else {
                                sendLegacy(emitter, "tool", WikiWebAssembler.toolEvent(tool), terminate);
                            }
                        },
                        activity -> {
                            if (agui) {
                                sendAgui(emitter, WikiAguiWebAssembler.activitySnapshot(traceId, activity), terminate);
                            }
                        });
                if (transportTerminated.get()) {
                    return null;
                }
                taskFinished.set(true);
                finishThinking(emitter, traceId, agui, thinkingStarted, terminate);
                if (terminalSent.compareAndSet(false, true)) {
                    if (agui) {
                        sendAgui(emitter, WikiAguiWebAssembler.runFinished(traceId, result), terminate);
                    } else {
                        sendLegacy(emitter, "citations", Map.of(
                                "type", "citations",
                                "citations", WikiWebAssembler.citationEvents(result)), terminate);
                        sendLegacy(emitter, "done", Map.of("type", "done", "reasoning", result.reasoning()), terminate);
                    }
                    emitter.complete();
                }
            } catch (RuntimeException exception) {
                if (!transportTerminated.get() && terminalSent.compareAndSet(false, true)) {
                    taskFinished.set(true);
                    finishThinking(emitter, traceId, agui, thinkingStarted, terminate);
                    if (agui) {
                        sendAgui(emitter, WikiAguiWebAssembler.runError(traceId, exception.getMessage()), terminate);
                    } else {
                        sendLegacy(emitter, "error", Map.of(
                                "type", "error", "message", errorMessage(exception)), terminate);
                    }
                    emitter.complete();
                } else {
                    log.debug("Wiki management chat stream terminated, spaceId={}", spaceId);
                }
            } finally {
                running.set(false);
                interrupt(heartbeatThread.get());
            }
            return null;
        });
        taskFuture.set(task);
        Thread worker = Thread.startVirtualThread(task);
        taskThread.compareAndSet(null, worker);
        if (transportTerminated.get()) {
            task.cancel(true);
        }
        return new Started(emitter, task);
    }

    private void finishThinking(SseEmitter emitter, String traceId, boolean agui,
                                AtomicBoolean thinkingStarted, Runnable terminate) {
        if (agui && thinkingStarted.compareAndSet(true, false)) {
            sendAgui(emitter, WikiAguiWebAssembler.thinkingEnd(traceId), terminate);
        }
    }

    private void startHeartbeat(SseEmitter emitter, AtomicBoolean running,
                                AtomicReference<Thread> heartbeatThread, Runnable terminate) {
        Thread heartbeat = Thread.startVirtualThread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(15_000L);
                } catch (InterruptedException exception) {
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
                } catch (IOException exception) {
                    terminate.run();
                    return;
                }
            }
        });
        heartbeatThread.set(heartbeat);
    }

    private void sendAgui(SseEmitter emitter, AguiStreamEventRes data, Runnable terminate) {
        send(emitter, SseEmitter.event().name(data.getType()).data(data, MediaType.APPLICATION_JSON), terminate);
    }

    private void sendLegacy(SseEmitter emitter, String name, Object data, Runnable terminate) {
        send(emitter, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON), terminate);
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event, Runnable terminate) {
        try {
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (IOException exception) {
            terminate.run();
            throw new SseTransportException(exception);
        }
    }

    private void interrupt(Thread thread) {
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    private String errorMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "问答失败，请稍后重试"
                : exception.getMessage();
    }

    private static final class SseTransportException extends RuntimeException {
        private SseTransportException(IOException cause) {
            super(cause);
        }
    }
}
