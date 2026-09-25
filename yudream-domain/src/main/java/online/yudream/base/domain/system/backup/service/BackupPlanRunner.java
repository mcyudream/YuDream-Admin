package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;

/**
 * 备份计划执行入口：由调度器（计划触发）或应用服务（手动触发）调用，入队一个远程备份任务。
 */
public interface BackupPlanRunner {

    void runPlan(BackupPlan plan, BackupJobTrigger trigger);
}
