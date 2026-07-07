package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;
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

@RestController
@RequestMapping("/api/platform/ai")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AiAppService aiAppService;

    @Value("${yudream.platform.ai.client.sse-timeout:10m}")
    private Duration sseTimeout;

    @PostMapping("/cms/pages/generate")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "使用 AI 为 CMS 生成页面草稿")
    public Result<CmsPageGenerateRes> generateCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        return Result.ok(AiWebAssembler.toRes(aiAppService.generateCmsPage(AiWebAssembler.toCmd(request))));
    }

    @PostMapping(value = "/cms/pages/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:ai:generate", name = "AI 流式生成页面", module = "平台能力", desc = "使用 AI 为 CMS 流式生成页面草稿")
    public SseEmitter streamCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        String traceId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> {
            AtomicBoolean running = new AtomicBoolean(true);
            CompletableFuture<Void> heartbeat = startHeartbeat(emitter, traceId, running);
            try {
                log.debug("AI SSE stream accepted, traceId={}, title={}, promptLength={}, image={}",
                        traceId,
                        request.getTitle(),
                        request.getPrompt() == null ? 0 : request.getPrompt().length(),
                        request.getImageDataUrl() != null && !request.getImageDataUrl().isBlank());
                send(emitter, AiWebAssembler.toProgressEvent(traceId, "accepted", "已收到请求，正在连接模型。"));
                var result = aiAppService.streamCmsPage(
                        AiWebAssembler.toCmd(request),
                        delta -> send(emitter, AiWebAssembler.toDeltaEvent(traceId, delta)),
                        tool -> {
                            send(emitter, AiWebAssembler.toProgressEvent(traceId, "tool", "正在更新画布。"));
                            send(emitter, AiWebAssembler.toToolEvent(traceId, tool));
                        },
                        progress -> send(emitter, AiWebAssembler.toProgressEvent(traceId, progress.action(), progress.content()))
                );
                log.debug("AI SSE stream completed, traceId={}, tools={}",
                        traceId,
                        result.getTools() == null ? 0 : result.getTools().size());
                send(emitter, AiWebAssembler.toResultEvent(traceId, result));
                emitter.complete();
            } catch (Exception e) {
                log.debug("AI SSE stream failed, traceId={}", traceId, e);
                send(emitter, AiWebAssembler.toErrorEvent(traceId, e.getMessage()));
                emitter.complete();
            } finally {
                running.set(false);
                heartbeat.cancel(true);
            }
        });
        return emitter;
    }

    private CompletableFuture<Void> startHeartbeat(SseEmitter emitter, String traceId, AtomicBoolean running) {
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
                send(emitter, AiWebAssembler.toProgressEvent(traceId, "heartbeat", "模型仍在生成中。"));
            }
        });
    }

    private void send(SseEmitter emitter, AiStreamEventRes data) {
        try {
            log.debug("AI SSE send event, traceId={}, event={}, action={}",
                    data.getTraceId(),
                    data.getEvent(),
                    data.getAction());
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(data.getEvent()).data(data));
            }
        } catch (IOException e) {
            log.debug("AI SSE send failed, traceId={}, event={}", data.getTraceId(), data.getEvent(), e);
            emitter.completeWithError(e);
        }
    }
}
