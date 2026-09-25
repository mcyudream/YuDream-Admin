package online.yudream.base.infra.system.backup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.repo.BackupJobRepo;
import online.yudream.base.domain.system.backup.repo.BackupPlanRepo;
import online.yudream.base.domain.system.backup.service.BackupJobRunner;
import online.yudream.base.domain.system.backup.valobj.BackupJobResult;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 备份任务队列执行器：单线程串行处理，宿主重启后中断任务标记失败；
 * 执行细节由应用层 BackupJobRunner 编排，进度经回调持久化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupJobQueueExecutor implements DisposableBean {

    private static final long IDLE_SLEEP_MILLIS = 500L;

    private final BackupJobRepo jobRepo;
    private final BackupPlanRepo planRepo;
    private final BackupJobRunner runner;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("backup-job-queue-", 0).factory());
    private volatile boolean running = true;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        recoverInterruptedJobs();
        worker.submit(this::loop);
    }

    private void recoverInterruptedJobs() {
        jobRepo.findByStatus(online.yudream.base.domain.system.backup.enumerate.BackupJobStatus.RUNNING)
                .forEach(job -> {
                    job.failInterrupted();
                    jobRepo.save(job);
                    log.warn("恢复中断的备份任务：{}", job.getId());
                });
    }

    private void loop() {
        while (running) {
            try {
                Optional<BackupJob> next = jobRepo.findNextQueued();
                if (next.isPresent()) {
                    execute(next.get());
                } else {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn("备份任务队列轮询失败：{}", exception.getMessage());
                sleepQuietly();
            }
        }
    }

    private void execute(BackupJob job) {
        log.info("备份任务开始执行 job={} type={}", job.getId(), job.getType());
        job.start();
        jobRepo.save(job);
        try {
            BackupJobResult result = runner.run(job, (phase, message, percent) -> {
                job.updateProgress(phase, message, percent);
                jobRepo.save(job);
            });
            job.succeed(result.archiveName(), result.archivePath(), result.archiveSize(),
                    result.stats(), result.warningMessage());
        } catch (Exception exception) {
            String message = exception.getMessage();
            job.fail(message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
            log.warn("备份任务 {} 失败：{}", job.getId(), job.getMessage());
        }
        jobRepo.save(job);
        markPlanRun(job);
    }

    private void markPlanRun(BackupJob job) {
        if (job.getPlanCode() == null) {
            return;
        }
        planRepo.findByCode(job.getPlanCode()).ifPresent(plan -> {
            plan.markRun(job.getId(), job.getStatus(), java.time.LocalDateTime.now());
            planRepo.save(plan);
        });
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(IDLE_SLEEP_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        running = false;
        worker.shutdownNow();
    }
}
