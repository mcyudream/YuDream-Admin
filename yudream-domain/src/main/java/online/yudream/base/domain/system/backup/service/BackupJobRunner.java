package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.valobj.BackupJobResult;

/**
 * 备份任务执行器：由基础设施队列调用，具体编排由应用层实现。
 * 抛出异常即任务失败；进度经回调上报，由队列负责持久化。
 */
public interface BackupJobRunner {

    BackupJobResult run(BackupJob job, BackupJobProgress progress);

    /** 进度回调。percent 传 -1 表示保持不变。 */
    interface BackupJobProgress {
        void update(String phase, String message, int percent);
    }
}
