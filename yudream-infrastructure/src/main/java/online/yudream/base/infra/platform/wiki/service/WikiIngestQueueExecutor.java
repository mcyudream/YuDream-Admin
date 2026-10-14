package online.yudream.base.infra.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIngestCancellationRegistry;
import online.yudream.base.domain.platform.wiki.service.WikiIngestProgressGateway;
import online.yudream.base.domain.platform.wiki.service.WikiIngestTaskRunner;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 持久化摄入队列执行器：单线程串行处理，崩溃恢复，自动重试，支持取消/手动重试与 SSE 进度广播。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"yudream.platform.capabilities.wiki.enabled", "yudream.platform.capabilities.ai.enabled"}, havingValue = "true")
public class WikiIngestQueueExecutor implements DisposableBean {

    private static final long IDLE_SLEEP_MILLIS = 500L;

    private final WikiIngestTaskRepo taskRepo;
    private final WikiIngestTaskRunner runner;
    private final WikiIngestProgressGateway progressGateway;
    private final WikiIngestCancellationRegistry cancellationRegistry;
    private final CapabilityModuleRepo capabilities;
    private online.yudream.base.domain.platform.ai.repo.AiRunRepo aiRuns;

    @org.springframework.beans.factory.annotation.Autowired
    void setAiRuns(online.yudream.base.domain.platform.ai.repo.AiRunRepo aiRuns) { this.aiRuns = aiRuns; }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("wiki-ingest-queue-", 0).factory()
    );
    private volatile boolean running = true;
    private boolean recovered;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker.submit(this::loop);
    }

    public void cancel(Long taskId) {
        taskRepo.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == WikiIngestTaskStatus.QUEUED) {
                task.cancel();
                taskRepo.save(task);
                publish(task, "任务已取消");
            }
            else if (task.getStatus() == WikiIngestTaskStatus.RUNNING) {
                cancellationRegistry.markCancelled(taskId);
            }
        });
    }

    public void retry(Long taskId) {
        taskRepo.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == WikiIngestTaskStatus.FAILED || task.getStatus() == WikiIngestTaskStatus.CANCELLED) {
                task.resetForRetry();
                taskRepo.save(task);
                publish(task, "任务已重新入队");
            }
        });
    }

    private void recoverInterruptedTasks() {
        taskRepo.findByStatus(WikiIngestTaskStatus.RUNNING).forEach(task -> {
            task.requeue();
            taskRepo.save(task);
            log.warn("恢复中断的摄入任务：{}", task.getId());
        });
    }

    private void loop() {
        while (running) {
            try {
                if (!enabled("wiki") || !enabled("ai")) {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                    continue;
                }
                if (!recovered) {
                    recoverInterruptedTasks();
                    recovered = true;
                }
                Optional<WikiIngestTask> next = taskRepo.findNextQueued();
                if (next.isPresent()) {
                    execute(next.get());
                }
                else {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                }
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            catch (Exception exception) {
                log.warn("摄入队列轮询失败：{}", exception.getMessage());
                sleepQuietly();
            }
        }
    }

    private boolean enabled(String code) {
        return capabilities.findByCode(code).map(module -> module.enabled()).orElse(false);
    }

    private void execute(WikiIngestTask task) {
        log.info("ingest execute start task={}", task.getId());
        task.start();
        taskRepo.save(task);
        publish(task, "任务开始执行");
        try {
            runner.run(task,
                    progress -> {
                        task.updateProgress(progress.phase(), progress.message(), progress.percent());
                        taskRepo.save(task);
                        publish(task, progress.message());
                    },
                    () -> cancelled(task));
            if (cancelled(task)) {
                task.cancel();
                taskRepo.save(task);
                publish(task, "任务已取消");
                return;
            }
            task.succeed();
            taskRepo.save(task);
            publish(task, "任务执行完成");
        }
        catch (Exception exception) {
            if (cancelled(task)) {
                task.cancel();
                taskRepo.save(task);
                publish(task, "任务已取消");
                return;
            }
            task.fail(readableMessage(exception));
            taskRepo.save(task);
            publish(task, task.getErrorMessage());
            if (task.canRetry()) {
                task.requeue();
                taskRepo.save(task);
                log.warn("摄入任务 {} 失败，自动重试（{}/{}）", task.getId(), task.getAttempts(), task.getMaxAttempts());
            }
            else {
                log.warn("摄入任务 {} 最终失败：{}", task.getId(), task.getErrorMessage());
            }
        }
        finally {
            // 用户停止会中断该任务的模型等待；串行队列还要处理下一项。应用关闭保留中断。
            if (running && cancelled(task)) Thread.interrupted();
            cancellationRegistry.clear(task.getId());
        }
    }

    private boolean cancelled(WikiIngestTask task) {
        if (cancellationRegistry.isCancelled(task.getId())) return true;
        return aiRuns != null && task.getAiRunId() != null && aiRuns.find(task.getAiRunId())
                .map(run -> run.isCancelRequested() || run.getStatus() == online.yudream.base.domain.platform.ai.enumerate.AiRunStatus.CANCELLED).orElse(false);
    }

    private void publish(WikiIngestTask task, String message) {
        try {
            progressGateway.publish(new WikiIngestProgress(
                    task.getId(),
                    task.getSpaceId(),
                    task.getSourceId(),
                    task.getPhase(),
                    message,
                    task.getPercent(),
                    task.getStatus() == WikiIngestTaskStatus.SUCCEEDED
                            || task.getStatus() == WikiIngestTaskStatus.FAILED
                            || task.getStatus() == WikiIngestTaskStatus.CANCELLED
            ));
        }
        catch (Exception exception) {
            // SSE 客户端断开等广播失败不影响任务执行与落库
            log.debug("摄入进度广播失败（{}）：{}", task.getId(), exception.getMessage());
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(IDLE_SLEEP_MILLIS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @Override
    public void destroy() {
        running = false;
        worker.shutdownNow();
    }
}
