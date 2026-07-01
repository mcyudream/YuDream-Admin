package online.yudream.base.infra.common.cache.preheat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.common.cache.prop.CacheProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 缓存预热启动器。
 * <p>
 * 应用启动完成后，按 {@link CachePreheatTask#order()} 顺序执行所有预热任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachePreheatBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private final List<CachePreheatTask> preheatTasks;
    private final CacheProperties cacheProperties;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!cacheProperties.isEnabled()) {
            log.info("Cache is disabled, skip preheat");
            return;
        }
        if (preheatTasks == null || preheatTasks.isEmpty()) {
            log.debug("No cache preheat task found");
            return;
        }
        preheatTasks.stream()
                .sorted(Comparator.comparingInt(CachePreheatTask::order))
                .forEach(task -> {
                    long start = System.currentTimeMillis();
                    try {
                        log.info("Cache preheat start, task={}", task.name());
                        task.preheat();
                        log.info("Cache preheat success, task={}, cost={}ms", task.name(), System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        log.error("Cache preheat failed, task={}, cost={}ms", task.name(), System.currentTimeMillis() - start, e);
                    }
                });
    }
}
