package online.yudream.base.interfaces.platform.chat.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.dto.ChatQuotaDTO;
import online.yudream.base.application.platform.chat.service.ChatAppService;
import online.yudream.base.application.platform.chat.service.ChatQuotaAppService;
import online.yudream.base.application.platform.chat.support.ChatStreamCancelledException;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.chat.assembler.ChatAguiWebAssembler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamSupport {

    private final ChatAppService chatAppService;
    private final ChatQuotaAppService quotaService;

    @Value("${yudream.platform.chat.sse-timeout:30m}")
    private Duration timeout;

    /**
     * Streams one non-persistent chat turn and coordinates transport cancellation with model generation.
     */
    public SseEmitter streamOnce(Long userId, ChatSendCmd cmd) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        StreamLifecycle lifecycle = new StreamLifecycle(emitter);
        String traceId = UUID.randomUUID().toString();
        lifecycle.start(() -> runStreamOnce(userId, cmd, traceId, lifecycle));
        return emitter;
    }

    /**
     * Streams a persistent chat turn and coordinates transport cancellation with model generation.
     */
    public SseEmitter stream(Long userId, ChatSendCmd cmd) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        StreamLifecycle lifecycle = new StreamLifecycle(emitter);
        String traceId = UUID.randomUUID().toString();
        lifecycle.start(() -> runStream(userId, cmd, traceId, lifecycle));
        return emitter;
    }

    private void runStreamOnce(Long userId, ChatSendCmd cmd, String traceId, StreamLifecycle lifecycle) {
        StreamCallbacks callbacks = new StreamCallbacks(traceId, lifecycle);
        FutureTask<Void> heartbeat = lifecycle.startHeartbeat();
        try {
            lifecycle.send(ChatAguiWebAssembler.runStarted(traceId));
            var result = chatAppService.streamOnce(
                    userId,
                    cmd,
                    callbacks::delta,
                    callbacks::reasoning,
                    callbacks::tool,
                    callbacks::activity);
            callbacks.endThinking();
            lifecycle.ensureActive();
            ChatQuotaDTO quota = quotaService.me(userId);
            Map<String, Object> resultPayload = new LinkedHashMap<>();
            resultPayload.put("content", result.content());
            resultPayload.put("reasoning", result.reasoning());
            resultPayload.put("citations", result.citations());
            resultPayload.put("usage", result.usage());
            resultPayload.put("usedTokens", quota.usedTokens());
            resultPayload.put("limitTokens", quota.limitTokens());
            resultPayload.put("remainingTokens", quota.remainingTokens());
            lifecycle.finish(ChatAguiWebAssembler.runFinished(traceId, resultPayload));
        }
        catch (ChatStreamCancelledException ignored) {
            log.debug("Chat AG-UI streamOnce cancelled, userId={}", userId);
        }
        catch (Exception error) {
            handleApplicationError(userId, traceId, lifecycle, callbacks, error, "streamOnce");
        }
        finally {
            callbacks.endThinkingIfActive();
            lifecycle.stopHeartbeat(heartbeat);
        }
    }

    private void runStream(Long userId, ChatSendCmd cmd, String traceId, StreamLifecycle lifecycle) {
        StreamCallbacks callbacks = new StreamCallbacks(traceId, lifecycle);
        FutureTask<Void> heartbeat = lifecycle.startHeartbeat();
        try {
            lifecycle.send(ChatAguiWebAssembler.runStarted(traceId));
            var result = chatAppService.send(
                    userId,
                    cmd,
                    callbacks::delta,
                    callbacks::reasoning,
                    callbacks::tool,
                    callbacks::activity);
            callbacks.endThinking();
            lifecycle.ensureActive();
            Map<String, Object> resultPayload = new LinkedHashMap<>();
            resultPayload.put("content", result.content());
            resultPayload.put("reasoning", result.reasoning());
            resultPayload.put("citations", result.citations());
            resultPayload.put("tools", result.tools());
            resultPayload.put("activities", result.activities());
            resultPayload.put("usage", result.usage());
            resultPayload.put("usedTokens", result.usedTokens());
            resultPayload.put("limitTokens", result.limitTokens());
            resultPayload.put("remainingTokens", result.remainingTokens());
            lifecycle.finish(ChatAguiWebAssembler.runFinished(traceId, resultPayload));
        }
        catch (ChatStreamCancelledException ignored) {
            log.debug("Chat AG-UI stream cancelled, userId={}", userId);
        }
        catch (Exception error) {
            handleApplicationError(userId, traceId, lifecycle, callbacks, error, "stream");
        }
        finally {
            callbacks.endThinkingIfActive();
            lifecycle.stopHeartbeat(heartbeat);
        }
    }

    private void handleApplicationError(Long userId, String traceId, StreamLifecycle lifecycle,
                                        StreamCallbacks callbacks, Exception error, String operation) {
        if (lifecycle.isCancelled() || Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            callbacks.endThinking();
            log.debug("Chat AG-UI {} failed, userId={}", operation, userId, error);
            lifecycle.fail(ChatAguiWebAssembler.runError(traceId, error.getMessage()));
        }
        catch (ChatStreamCancelledException ignored) {
            log.debug("Chat AG-UI {} disconnected while reporting error, userId={}", operation, userId);
        }
    }

    private static final class StreamCallbacks {
        private final String traceId;
        private final StreamLifecycle lifecycle;
        private final AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        private final AtomicBoolean thinkingEnded = new AtomicBoolean(false);
        private final AtomicInteger toolSequence = new AtomicInteger();

        private StreamCallbacks(String traceId, StreamLifecycle lifecycle) {
            this.traceId = traceId;
            this.lifecycle = lifecycle;
        }

        private void delta(String value) {
            lifecycle.send(ChatAguiWebAssembler.textChunk(traceId, value));
        }

        private void reasoning(String value) {
            lifecycle.ensureActive();
            if (value == null || value.isEmpty() || thinkingEnded.get()) {
                return;
            }
            if (thinkingStarted.compareAndSet(false, true)) {
                lifecycle.send(ChatAguiWebAssembler.thinkingStart(traceId));
            }
            lifecycle.send(ChatAguiWebAssembler.thinkingContent(traceId, value));
        }

        private void tool(online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult value) {
            lifecycle.ensureActive();
            String toolCallId = traceId + "-tool-" + toolSequence.incrementAndGet();
            lifecycle.send(ChatAguiWebAssembler.toolStart(traceId, toolCallId, value));
            lifecycle.send(ChatAguiWebAssembler.toolResult(traceId, toolCallId, value));
        }

        private void activity(online.yudream.base.domain.platform.chat.valobj.ChatActivity value) {
            lifecycle.send(ChatAguiWebAssembler.activitySnapshot(traceId, value));
        }

        private void endThinking() {
            if (thinkingStarted.get() && thinkingEnded.compareAndSet(false, true)) {
                lifecycle.send(ChatAguiWebAssembler.thinkingEnd(traceId));
            }
        }

        private void endThinkingIfActive() {
            if (!lifecycle.isCancelled() && !lifecycle.isTerminal()) {
                endThinking();
            }
        }
    }

    private static final class StreamLifecycle {
        private final SseEmitter emitter;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        private final AtomicReference<Future<?>> task = new AtomicReference<>();

        private StreamLifecycle(SseEmitter emitter) {
            this.emitter = emitter;
            emitter.onCompletion(this::cancelFromClient);
            emitter.onTimeout(this::cancelFromClient);
            emitter.onError(ignored -> cancelFromClient());
        }

        private void start(Runnable action) {
            FutureTask<Void> future = new FutureTask<>(() -> {
                action.run();
                return null;
            });
            task.set(future);
            if (cancelled.get()) {
                future.cancel(true);
                return;
            }
            Thread.startVirtualThread(future);
        }

        private FutureTask<Void> startHeartbeat() {
            FutureTask<Void> heartbeat = new FutureTask<>(() -> {
                while (!cancelled.get() && !terminal.get()) {
                    try {
                        Thread.sleep(15_000L);
                    }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    if (cancelled.get() || terminal.get()) {
                        return null;
                    }
                    sendHeartbeat();
                }
                return null;
            });
            Thread.startVirtualThread(heartbeat);
            return heartbeat;
        }

        private void stopHeartbeat(FutureTask<Void> heartbeat) {
            heartbeat.cancel(true);
        }

        private void send(AguiStreamEventRes data) {
            ensureActive();
            try {
                synchronized (emitter) {
                    ensureActive();
                    emitter.send(SseEmitter.event().name(data.getType()).data(data, MediaType.APPLICATION_JSON));
                }
            }
            catch (IOException error) {
                cancelAfterSendFailure(data.getType(), error);
                throw new ChatStreamCancelledException(error);
            }
        }

        private void sendHeartbeat() {
            ensureActive();
            try {
                synchronized (emitter) {
                    ensureActive();
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
            }
            catch (IOException error) {
                cancelAfterSendFailure("heartbeat", error);
                throw new ChatStreamCancelledException(error);
            }
        }

        private void finish(AguiStreamEventRes event) {
            sendTerminal(event);
        }

        private void fail(AguiStreamEventRes event) {
            sendTerminal(event);
        }

        private void sendTerminal(AguiStreamEventRes event) {
            ensureActive();
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                synchronized (emitter) {
                    if (cancelled.get()) {
                        throw new ChatStreamCancelledException();
                    }
                    emitter.send(SseEmitter.event().name(event.getType()).data(event, MediaType.APPLICATION_JSON));
                }
                emitter.complete();
            }
            catch (IOException error) {
                cancelAfterSendFailure(event.getType(), error);
                throw new ChatStreamCancelledException(error);
            }
        }

        private void cancelAfterSendFailure(String eventType, IOException error) {
            log.debug("Chat AG-UI send failed, type={}", eventType, error);
            if (cancelled.compareAndSet(false, true)) {
                Future<?> runningTask = task.get();
                if (runningTask != null) {
                    runningTask.cancel(true);
                }
            }
            emitter.completeWithError(error);
        }

        private void cancelFromClient() {
            if (terminal.get() || !cancelled.compareAndSet(false, true)) {
                return;
            }
            Future<?> runningTask = task.get();
            if (runningTask != null) {
                runningTask.cancel(true);
            }
        }

        private void ensureActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new ChatStreamCancelledException();
            }
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private boolean isTerminal() {
            return terminal.get();
        }
    }
}
