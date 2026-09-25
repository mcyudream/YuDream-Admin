package online.yudream.base.infra.system.backup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;
import online.yudream.base.domain.system.backup.event.BackupPlansChangedEvent;
import online.yudream.base.domain.system.backup.repo.BackupPlanRepo;
import online.yudream.base.domain.system.backup.service.BackupPlanRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 备份计划调度器：按计划 cron（Spring 6 位表达式，含秒）在到点时调用计划执行入口入队任务。
 * 计划增删改/启停后应用层发布 BackupPlansChangedEvent，本调度器整体重排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupPlanScheduler {

    private final BackupPlanRepo planRepo;
    private final BackupPlanRunner planRunner;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("backup-plan-scheduler-", 0).factory());
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        rescheduleAll();
    }

    @EventListener(BackupPlansChangedEvent.class)
    public void onPlansChanged() {
        rescheduleAll();
    }

    public synchronized void rescheduleAll() {
        scheduled.values().forEach(future -> future.cancel(false));
        scheduled.clear();
        for (BackupPlan plan : planRepo.findByEnabled(true)) {
            try {
                CronExpression expression = CronExpression.parse(plan.getCron());
                schedulePlan(plan, expression);
            } catch (IllegalArgumentException e) {
                log.warn("备份计划 {} 的 cron 表达式非法，已跳过调度：{}", plan.getCode(), plan.getCron());
            }
        }
        log.debug("备份计划调度完成：{} 个生效", scheduled.size());
    }

    private void schedulePlan(BackupPlan plan, CronExpression expression) {
        LocalDateTime next = expression.next(LocalDateTime.now());
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(LocalDateTime.now(), next).toMillis());
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                planRunner.runPlan(plan, BackupJobTrigger.SCHEDULED);
            } catch (Exception e) {
                log.warn("备份计划 {} 触发失败：{}", plan.getCode(), e.getMessage());
            }
            reschedulePlan(plan);
        }, delayMillis, TimeUnit.MILLISECONDS);
        scheduled.put(plan.getId(), future);
    }

    private void reschedulePlan(BackupPlan plan) {
        planRepo.findByCode(plan.getCode())
                .filter(BackupPlan::isEnabled)
                .ifPresent(latest -> {
                    try {
                        schedulePlan(latest, CronExpression.parse(latest.getCron()));
                    } catch (IllegalArgumentException e) {
                        log.warn("备份计划 {} 的 cron 表达式非法，已停止调度：{}", latest.getCode(), latest.getCron());
                    }
                });
    }
}
