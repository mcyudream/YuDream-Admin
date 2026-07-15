package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.BuiltinAgentCodes;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/platform/ai")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AiAppService aiAppService;

    @Value("${yudream.platform.ai.client.sse-timeout:30m}")
    private Duration sseTimeout;

    @PostMapping("/cms/pages/generate")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "使用 AI 为 CMS 生成页面草稿")
    public Result<CmsPageGenerateRes> generateCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        return Result.ok(AiWebAssembler.toRes(aiAppService.generateCmsPage(
                AiWebAssembler.toCmd(request, SecurityPrincipalSupport.current())
        )));
    }

    @PostMapping(value = "/cms/pages/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:ai:generate", name = "AI 流式生成页面", module = "平台能力", desc = "使用 AI 为 CMS 流式生成页面草稿")
    public SseEmitter streamCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        String traceId = UUID.randomUUID().toString();
        AtomicInteger toolSequence = new AtomicInteger();
        var command = AiWebAssembler.toCmd(request, SecurityPrincipalSupport.current());
        CompletableFuture.runAsync(() -> {
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicBoolean activityStarted = new AtomicBoolean(false);
            CompletableFuture<Void> heartbeat = null;
            try {
                log.debug("AI SSE stream accepted, traceId={}, title={}, promptLength={}, image={}",
                        traceId,
                        request.getTitle(),
                        request.getPrompt() == null ? 0 : request.getPrompt().length(),
                        request.getImageDataUrl() != null && !request.getImageDataUrl().isBlank());
                send(emitter, AiWebAssembler.toAguiRunStarted(traceId));
                sendActivity(emitter, traceId, activityStarted, "accepted", "已收到请求，正在连接模型。");
                heartbeat = startHeartbeat(emitter, traceId, running, activityStarted);
                var result = aiAppService.streamCmsPage(
                        command,
                        delta -> send(emitter, AiWebAssembler.toAguiTextChunk(traceId, delta)),
                        tool -> {
                            String toolCallId = traceId + "-tool-" + toolSequence.incrementAndGet();
                            send(emitter, AiWebAssembler.toAguiToolStart(traceId, toolCallId, tool));
                            send(emitter, AiWebAssembler.toAguiToolResult(traceId, toolCallId, tool));
                        },
                        progress -> sendActivity(emitter, traceId, activityStarted, progress.action(), progress.content())
                );
                log.debug("AI SSE stream completed, traceId={}, tools={}",
                        traceId,
                        result.getTools() == null ? 0 : result.getTools().size());
                if (BuiltinAgentCodes.AGUI_CARD.equals(request.getAgentCode())) {
                    send(emitter, AiWebAssembler.toAguiCardSnapshot(traceId, result.getSummary()));
                }
                send(emitter, AiWebAssembler.toAguiRunFinished(traceId, result));
                emitter.complete();
            } catch (Exception e) {
                log.debug("AI SSE stream failed, traceId={}", traceId, e);
                send(emitter, AiWebAssembler.toAguiRunError(traceId, e.getMessage()));
                emitter.complete();
            } finally {
                running.set(false);
                if (heartbeat != null) {
                    heartbeat.cancel(true);
                }
            }
        });
        return emitter;
    }

    private CompletableFuture<Void> startHeartbeat(
            SseEmitter emitter,
            String traceId,
            AtomicBoolean running,
            AtomicBoolean activityStarted
    ) {
        return CompletableFuture.runAsync(() -> {
            int count = 0;
            while (running.get()) {
                try {
                    Thread.sleep(4_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!running.get()) {
                    return;
                }
                count++;
                log.debug("AI SSE heartbeat, traceId={}, count={}", traceId, count);
                sendActivity(emitter, traceId, activityStarted, "heartbeat", "模型仍在生成中。");
            }
        });
    }

    private void sendActivity(
            SseEmitter emitter,
            String traceId,
            AtomicBoolean activityStarted,
            String action,
            String content
    ) {
        if (activityStarted.compareAndSet(false, true)) {
            send(emitter, AiWebAssembler.toAguiActivitySnapshot(traceId, action, content));
            return;
        }
        send(emitter, AiWebAssembler.toAguiActivityDelta(traceId, action, content));
    }

    private void send(SseEmitter emitter, AguiStreamEventRes data) {
        try {
            log.debug("AI AG-UI SSE send event, type={}, runId={}", data.getType(), data.getRunId());
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(data.getType()).data(data));
            }
        } catch (IOException e) {
            log.debug("AI AG-UI SSE send failed, type={}", data.getType(), e);
            emitter.completeWithError(e);
        }
    }
}
