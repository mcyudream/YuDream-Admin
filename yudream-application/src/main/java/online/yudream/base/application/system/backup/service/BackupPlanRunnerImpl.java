package online.yudream.base.application.system.backup.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;
import online.yudream.base.domain.system.backup.enumerate.BackupJobType;
import online.yudream.base.domain.system.backup.repo.BackupJobRepo;
import online.yudream.base.domain.system.backup.repo.RemoteTargetRepo;
import online.yudream.base.domain.system.backup.service.BackupPlanRunner;
import org.springframework.stereotype.Service;

/**
 * 备份计划执行实现：把计划翻译为一个 REMOTE_BACKUP 任务入队（调度器到点与手动触发共用）。
 */
@Service
@RequiredArgsConstructor
public class BackupPlanRunnerImpl implements BackupPlanRunner {

    private final BackupJobRepo jobRepo;
    private final RemoteTargetRepo targetRepo;

    @Override
    public void runPlan(BackupPlan plan, BackupJobTrigger trigger) {
        RemoteTarget target = targetRepo.findByCode(plan.getTargetCode())
                .orElseThrow(() -> new BizException("异地目标不存在：" + plan.getTargetCode()));
        if (!target.isEnabled()) {
            throw new BizException("异地目标已停用：" + target.getCode());
        }
        jobRepo.save(BackupJob.create(BackupJobType.REMOTE_BACKUP, trigger, plan.getScopeTags(),
                null, plan.getCode(), target.getCode(), target.getName()));
    }
}
