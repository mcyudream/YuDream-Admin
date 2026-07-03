package online.yudream.base.domain.platform.capability.repo;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;

import java.util.List;
import java.util.Optional;

public interface CapabilityModuleRepo {

    CapabilityModule save(CapabilityModule module);

    Optional<CapabilityModule> findByCode(String code);

    List<CapabilityModule> findAll();
}
