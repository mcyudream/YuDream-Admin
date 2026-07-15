package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.service.WikiPublicationProgressGateway;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiProgressWebAssembler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiPublicationProgressController {
    private final WikiPublicationProgressGateway progress;

    @GetMapping("/nodes/{nodeId}/publication-events")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 发布进度", module = "平台能力", desc = "订阅 Wiki 向量化和图谱抽取实时进度")
    public SseEmitter events(@PathVariable Long nodeId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        try {
            AutoCloseable subscription = progress.subscribe(nodeId, item -> send(emitter, item));
            emitter.onCompletion(() -> close(subscription));
            emitter.onTimeout(() -> close(subscription));
            emitter.send(SseEmitter.event().name("wiki.progress").data("{\"event\":\"wiki.progress\",\"action\":\"subscribed\",\"module\":\"wiki\"}"));
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, online.yudream.base.domain.platform.wiki.valobj.WikiPublicationProgress item) {
        try {
            emitter.send(SseEmitter.event().name("wiki.progress").data(WikiProgressWebAssembler.toRes(item)));
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
    }

    private void close(AutoCloseable subscription) {
        try {
            subscription.close();
        } catch (Exception ignored) {
        }
    }
}
