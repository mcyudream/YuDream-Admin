package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.wiki.support.WikiPublicationProgressStreamSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiPublicationProgressController {

    private final WikiPublicationProgressStreamSupport progress;

    @GetMapping("/nodes/{nodeId}/publication-events")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 发布进度", module = "平台能力", desc = "订阅 Wiki 向量化和图谱抽取实时进度")
    public SseEmitter events(@PathVariable Long nodeId) {
        return progress.subscribe(nodeId);
    }
}
