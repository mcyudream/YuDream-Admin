package online.yudream.base.domain.platform.satori.repo;

import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;

import java.util.List;
import java.util.Optional;

public interface SatoriLoginRepo {
    SatoriLogin save(SatoriLogin login);
    Optional<SatoriLogin> findByNaturalKey(Long connectionId, String platform, String userId);
    List<SatoriLogin> findByConnectionId(Long connectionId);
}
