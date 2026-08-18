package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;

import java.util.function.Consumer;

/**
 * 摄入进度事件端口（SSE 广播）。
 */
public interface WikiIngestProgressGateway {

    void publish(WikiIngestProgress progress);

    AutoCloseable subscribe(Long spaceId, Consumer<WikiIngestProgress> consumer);
}
