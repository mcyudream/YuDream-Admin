package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/platform/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AiAppService aiAppService;

    @PostMapping("/cms/pages/generate")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "使用 AI 为 CMS 生成页面草稿")
    public Result<CmsPageGenerateRes> generateCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        return Result.ok(AiWebAssembler.toRes(aiAppService.generateCmsPage(AiWebAssembler.toCmd(request))));
    }

    @PostMapping(value = "/cms/pages/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:ai:generate", name = "AI 流式生成页面", module = "平台能力", desc = "使用 AI 为 CMS 流式生成页面草稿")
    public SseEmitter streamCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        CompletableFuture.runAsync(() -> {
            try {
                var result = aiAppService.streamCmsPage(
                        AiWebAssembler.toCmd(request),
                        delta -> send(emitter, "delta", AiWebAssembler.toDeltaRes(delta)),
                        tool -> send(emitter, "tool", AiWebAssembler.toToolEventRes(tool))
                );
                send(emitter, "result", AiWebAssembler.toResultRes(result));
                emitter.complete();
            } catch (Exception e) {
                send(emitter, "error", AiWebAssembler.toErrorRes(e.getMessage()));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
