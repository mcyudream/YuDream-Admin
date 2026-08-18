package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.service.WikiIngestProgressGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 内存版摄入进度事件总线（按 spaceId 隔离订阅者）。
 */
@Service
public class InMemoryWikiIngestProgressGateway implements WikiIngestProgressGateway {

    private final Map<Long, CopyOnWriteArrayList<Consumer<WikiIngestProgress>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(WikiIngestProgress progress) {
        if (progress == null || progress.spaceId() == null) {
            return;
        }
        CopyOnWriteArrayList<Consumer<WikiIngestProgress>> consumers = listeners.get(progress.spaceId());
        if (consumers != null) {
            consumers.forEach(consumer -> consumer.accept(progress));
        }
    }

    @Override
    public AutoCloseable subscribe(Long spaceId, Consumer<WikiIngestProgress> consumer) {
        CopyOnWriteArrayList<Consumer<WikiIngestProgress>> consumers =
                listeners.computeIfAbsent(spaceId, key -> new CopyOnWriteArrayList<>());
        consumers.add(consumer);
        return () -> consumers.remove(consumer);
    }
}
