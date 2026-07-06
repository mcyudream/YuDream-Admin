package online.yudream.base.domain.system.dashboard.repo;

import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;

import java.util.Optional;

public interface DashboardLayoutRepo {

    DashboardLayout save(DashboardLayout layout);

    Optional<DashboardLayout> findByOwner(DashboardLayoutOwnerType ownerType, Long ownerId);

    void deleteByOwner(DashboardLayoutOwnerType ownerType, Long ownerId);
}
