package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.wiki.support.WikiIngestProgressStreamSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 摄入队列 SSE 进度。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiIngestProgressController {

    private final WikiIngestProgressStreamSupport progress;

    @GetMapping("/spaces/{spaceId}/ingest-events")
    @PermissionRegister(code = "platform:wiki:view", name = "查看摄入进度", module = "平台能力", desc = "订阅知识库摄入队列实时进度")
    public SseEmitter events(@PathVariable Long spaceId) {
        return progress.subscribe(spaceId);
    }
}
