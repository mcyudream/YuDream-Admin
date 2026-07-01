package online.yudream.base.infra.common.cache.preheat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 缓存预热服务。
 * <p>
 * 支持手动触发所有预热任务或指定任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachePreheatService {

    private final List<CachePreheatTask> preheatTasks;

    /**
     * 手动触发所有预热任务。
     */
    public void preheatAll() {
        if (preheatTasks == null || preheatTasks.isEmpty()) {
            log.warn("No cache preheat task found");
            return;
        }
        preheatTasks.stream()
                .sorted(Comparator.comparingInt(CachePreheatTask::order))
                .forEach(this::execute);
    }

    /**
     * 手动触发指定名称的预热任务。
     */
    public void preheat(String taskName) {
        if (preheatTasks == null) {
            return;
        }
        preheatTasks.stream()
                .filter(task -> task.name().equals(taskName))
                .findFirst()
                .ifPresentOrElse(
                        this::execute,
                        () -> log.warn("Cache preheat task not found, name={}", taskName)
                );
    }

    private void execute(CachePreheatTask task) {
        long start = System.currentTimeMillis();
        try {
            log.info("Manual cache preheat start, task={}", task.name());
            task.preheat();
            log.info("Manual cache preheat success, task={}, cost={}ms", task.name(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Manual cache preheat failed, task={}", task.name(), e);
        }
    }
}
