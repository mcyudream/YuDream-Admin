package online.yudream.base.domain.system.backup.repo;

import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;

import java.util.List;
import java.util.Optional;

public interface BackupJobRepo {

    BackupJob save(BackupJob job);

    Optional<BackupJob> findById(Long id);

    Optional<BackupJob> findNextQueued();

    List<BackupJob> findByStatus(BackupJobStatus status);

    List<BackupJob> findRecent(int limit);

    List<BackupJob> findByPlanCodeAndStatus(String planCode, BackupJobStatus status);

    void deleteById(Long id);
}
