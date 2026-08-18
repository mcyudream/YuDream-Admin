package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.service.WikiIngestCancellationRegistry;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryWikiIngestCancellationRegistry implements WikiIngestCancellationRegistry {

    private final Set<Long> cancelled = ConcurrentHashMap.newKeySet();

    @Override
    public void markCancelled(Long taskId) {
        if (taskId != null) {
            cancelled.add(taskId);
        }
    }

    @Override
    public boolean isCancelled(Long taskId) {
        return taskId != null && cancelled.contains(taskId);
    }

    @Override
    public void clear(Long taskId) {
        if (taskId != null) {
            cancelled.remove(taskId);
        }
    }
}
