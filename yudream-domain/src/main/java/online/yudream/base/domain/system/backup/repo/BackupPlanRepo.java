package online.yudream.base.domain.system.backup.repo;

import online.yudream.base.domain.system.backup.aggregate.BackupPlan;

import java.util.List;
import java.util.Optional;

public interface BackupPlanRepo {

    BackupPlan save(BackupPlan plan);

    Optional<BackupPlan> findById(Long id);

    Optional<BackupPlan> findByCode(String code);

    List<BackupPlan> findAll();

    List<BackupPlan> findByEnabled(boolean enabled);

    void deleteById(Long id);
}
