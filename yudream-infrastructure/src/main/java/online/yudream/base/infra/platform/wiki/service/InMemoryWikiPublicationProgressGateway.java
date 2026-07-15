package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.service.WikiPublicationProgressGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiPublicationProgress;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class InMemoryWikiPublicationProgressGateway implements WikiPublicationProgressGateway {
    private final Map<Long, CopyOnWriteArrayList<Consumer<WikiPublicationProgress>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(WikiPublicationProgress progress) {
        subscribers.getOrDefault(progress.nodeId(), new CopyOnWriteArrayList<>()).forEach(listener -> listener.accept(progress));
    }

    @Override
    public AutoCloseable subscribe(Long nodeId, Consumer<WikiPublicationProgress> consumer) {
        subscribers.computeIfAbsent(nodeId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        return () -> subscribers.computeIfPresent(nodeId, (ignored, listeners) -> {
            listeners.remove(consumer);
            return listeners.isEmpty() ? null : listeners;
        });
    }
}
